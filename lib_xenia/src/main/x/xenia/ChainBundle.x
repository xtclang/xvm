import web.AcceptList;
import web.Catalog.EndpointInfo;
import web.Catalog.MethodInfo;
import web.Catalog.WebServiceInfo;
import web.ErrorHandler;
import web.HttpMethod;
import web.HttpStatus;
import web.MediaType;
import web.ParameterBinding;
import web.PathParam;
import web.QueryParam;
import web.Response;
import web.Session;
import web.WebService;

import web.responses.SimpleResponse;


/**
 * The chain bundle represents a set of lazily created call chain collections.
 */
class ChainBundle(Catalog catalog)
    {
    /**
     * The busy indicator.
     */
    Boolean isBusy;

    /**
     * The WebServices indexed by the service id.
     */
    WebService?[] services;

    /**
     * The CallChains indexed by the endpoint id.
     */
    Handler?[] chains;

    /**
     * Obtain a call chain for the specified endpoint.
     */
    Handler ensureCallChain(EndpointInfo endpoint)
        {
        Handler? handle = chains[endpoint.id];
        if (handle != Null)
            {
            return handle;
            }

        Int          wsid             = endpoint.wsid;
        HttpMethod   httpMethod       = endpoint.httpMethod;
        MethodInfo[] interceptorInfos = collectInterceptors(wsid, httpMethod);
        MethodInfo[] observerInfos    = collectObservers(wsid, httpMethod);

        Method<WebService> method  = endpoint.method;
        ParameterBinder[]  binders = new ParameterBinder[];

        for (Parameter param : method.params)
            {
            assert String name := param.hasName();

            if (param.is(ParameterBinding))
                {
                name ?= param.templateParameter;

                if (param.is(QueryParam))
                    {
                    // TODO GG: .as(Parameter) should not be needed
                    binders += (session, request, values) ->
                        extractQueryValue(request, name, param.as(Parameter), values);
                    continue;
                    }
                if (param.is(PathParam))
                    {
                    assert endpoint.template.vars.contains(name);

                    binders += (session, request, values) ->
                        extractPathValue(request, name, param.as(Parameter), values);
                    continue;
                    }
                throw new IllegalState($"Unsupported ParameterBinder {param.ParamType}");
                }

            if (endpoint.template.vars.contains(name))
                {
                binders += (session, request, values) ->
                    extractPathValue(request, name, param, values);
                continue;
                }

            if (param.ParamType == Session)
                {
                binders += (session, request, values) -> values.add(session);
                continue;
                }

            if (param.ParamType == Request)
                {
                binders += (session, request, values) -> values.add(request);
                continue;
                }

            if (param.ParamType defaultValue := param.defaultValue())
                {
                binders += (session, request, values) -> values.add(defaultValue);
                continue;
                }

            throw new IllegalState($"Unresolved parameter: {name.quoted()} for method {method}");
            }

        typedef Method<WebService, <Session, Request, Handler>, <Response>> as InterceptorMethod;
        typedef Method<WebService, <Session, Request>, <>>                  as ObserverMethod;

        // start with the innermost endpoint
        WebService webService  = ensureWebService(wsid);
        Function   boundMethod = method.bindTarget(webService);
        Responder  respond     = generateResponder(endpoint);

        handle = binders.empty
            ? ((session, request) -> respond(request, boundMethod()))
            : ((session, request) ->
                {
                Tuple values = Tuple:();
                for (ParameterBinder bind : binders)
                    {
                    values = bind(session, request, values);
                    }
                return respond(request, boundMethod.invoke(values));
                });

        for (MethodInfo info : interceptorInfos)
            {
            Method methodNext = info.method;
            Int    wsidNext   = info.wsid;
            if (wsidNext != wsid)
                {
                // call to a different service; need to generate a WebService.route() "preamble"
                // even if the service doesn't have any interceptors (it that case it must have an
                // error handler or an explicitly defined "route()" method)
                webService  = ensureWebService(wsidNext);
                wsidNext    = wsid;

                ErrorHandler? onError = makeErrorHandler(wsid, webService);

                handle = (session, request) -> webService.route(session, request, handle, onError);
                }

            if (methodNext.is(InterceptorMethod))
                {
                Interceptor        interceptor  = methodNext.bindTarget(webService);
                Parameter<Handler> handlerParam = interceptor.params[2].as(Parameter<Handler>);

                handle = interceptor.bind(handlerParam, handle).as(Handler);
                }
            }

        Int observerCount = observerInfos.size;
        if (observerCount > 0)
            {
            Observer[] observers = new Observer[observerCount] (i ->
                    {
                    MethodInfo info = observerInfos[i];
                    return info.method.as(ObserverMethod).bindTarget(ensureWebService(info.wsid));
                    });

            handle = (session, request) ->
                {
                // observers are not handlers and call asynchronously
                for (Observer observe : observers)
                    {
                    observe^(session, request);
                    }
                return handle(session, request);
                };
            }

        // the chain always starts with a WebService.route() "preamble"
        ErrorHandler? onError = makeErrorHandler(wsid, webService);

        handle = (session, request) -> webService.route(session, request, handle, onError);

        chains[endpoint.id] = handle;
        return handle;
        }

    /**
     * Collect interceptors for the specified service. Note, that if a service in the path doesn't
     * have any interceptors, but has an explicitly defined "route" method or an error handler,
     * we still need to include it in the list.
     */
    MethodInfo[] collectInterceptors(Int wsid, HttpMethod httpMethod)
        {
        WebServiceInfo[] services = catalog.services;
        String           path     = services[wsid].path;

        MethodInfo[] interceptors = [];
        for (Int id : 0..wsid)
            {
            WebServiceInfo serviceInfo = services[id];
            if (path.startsWith(serviceInfo.path))
                {
                if (serviceInfo.interceptors.empty)
                    {
                    if (MethodInfo onErrorInfo ?= serviceInfo.onError)
                        {
                        interceptors.add(onErrorInfo);
                        }
                    if (MethodInfo routeInfo ?= serviceInfo.route)
                        {
                        interceptors.add(routeInfo);
                        }
                    }
                else
                    {
                    serviceInfo.interceptors.filter(m -> m.httpMethod == httpMethod, interceptors);
                    }
                }
            }
        return interceptors.makeImmutable();
        }

    /**
     * Collect observers for the specified WebService.
     */
    MethodInfo[] collectObservers(Int wsid, HttpMethod httpMethod)
        {
        WebServiceInfo[] services = catalog.services;
        String           path     = services[wsid].path;

        MethodInfo[] observers = [];
        for (Int id : 0..wsid)
            {
            WebServiceInfo serviceInfo = services[id];
            if (path.startsWith(serviceInfo.path))
                {
                serviceInfo.observers.filter(m -> m.httpMethod == httpMethod, observers);
                }
            }
        return observers.makeImmutable();
        }

    /**
     * Create an error handler for the specified WebService.
     */
     ErrorHandler? makeErrorHandler(Int wsid, WebService webService)
        {
        typedef Method<WebService, <Session, Request, Exception|String|HttpStatus>, <Response>>
            as ErrorMethod;

        MethodInfo? onErrorInfo = catalog.services[wsid].onError;
        return  onErrorInfo == Null
                ? Null
                : onErrorInfo.method.as(ErrorMethod).bindTarget(webService);
        }

    /**
     * Extract the path value from the Request and append it to the values Tuple.
     */
    private static Tuple extractPathValue(Request request, String path, Parameter param, Tuple values)
        {
        Object paramValue;
        if (UriTemplate.Value value := request.matchResult.get(path))
            {
            // TODO: convert
            paramValue = value;
            }
        else if (param.ParamType defaultValue := param.defaultValue())
            {
            paramValue = defaultValue;
            }
        else
            {
            throw new IllegalState($"Missing path parameter: {path.quoted()}");
            }
        return values.add(paramValue.as(param.ParamType));
        }

    /**
     * Extract the query value from the Request and append it to the values Tuple.
     */
    private static Tuple extractQueryValue(Request request, String name, Parameter param, Tuple values)
        {
        Object paramValue;
        if (UriTemplate.Value value := request.queryParams.get(name))
            {
            // TODO: convert
            paramValue = value;
            }
        else if (param.ParamType defaultValue := param.defaultValue())
            {
            paramValue = defaultValue;
            }
        else
            {
            throw new IllegalState($"Missing path parameter: {name.quoted}");
            }
        return values.add(paramValue.as(param.ParamType));
        }

    /**
     * Ensure a WebService instance for the specified id.
     */
    private WebService ensureWebService(Int wsid)
        {
        WebService? svc = services[wsid];
        if (svc != Null)
            {
            return svc;
            }

        svc = catalog.services[wsid].constructor();
        services[wsid] = svc;
        return svc;
        }

    /**
     * Generate a response handler for the specified endpoint.
     */
    private Responder generateResponder(EndpointInfo endpoint)
        {
        if (endpoint.conditionalResult)
            {
            // the method executed is a conditional method that has returned False as the
            // first element of the Tuple
            return (request, result) -> result[0].as(Boolean)
                   ? createSimpleResponse(endpoint, request, result[1])
                   : TODO new SimpleResponse(HttpStatus.NotFound);
            }

        return (request, result) -> createSimpleResponse(endpoint, request, result[0]);
        }

    /**
     * Create an HTTP response.
     */
    Response createSimpleResponse(EndpointInfo endpoint, Request request, Object result)
        {
        AcceptList accepts   = request.accepts;
        MediaType  mediaType = endpoint.resolveResponseContentType(accepts);

        TODO the codec stuff goes here

        TODO return new Response(result, mediaType, codec);
        }
    }