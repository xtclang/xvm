import web.AcceptList;
import web.Catalog.EndpointInfo;
import web.Catalog.MethodInfo;
import web.Catalog.WebServiceInfo;
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
     * TODO
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
        MethodInfo?  onErrorInfo      = findOnError(wsid);

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

            if (name == "session" && param.ParamType == Session)
                {
                binders += (session, request, values) -> values.add(session);
                continue;
                }

            if (name == "request" && param.ParamType == Request)
                {
                binders += (session, request, values) -> values.add(request);
                continue;
                }

            if (param.ParamType defaultValue := param.defaultValue())
                {
                binders += (session, request, values) -> values.add(defaultValue);
                continue;
                }

            throw new IllegalState($"Unresolved parameter: {name.quoted()}");
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

        for (MethodInfo methodInfo : interceptorInfos)
            {
            InterceptorMethod methodNext = methodInfo.method.as(InterceptorMethod);
            Int               wsidNext   = methodInfo.wsid;
            if (wsidNext != wsid)
                {
                // call to a different service; need to generate a WebService.route() "preamble"
                webService = ensureWebService(wsidNext);
                wsidNext   = wsid;
                handle     = (session, request) -> webService.route(session, request, handle);
                }
            Interceptor        interceptor  = methodNext.bindTarget(webService);
            Parameter<Handler> handlerParam = interceptor.params[2].as(Parameter<Handler>);

            handle = interceptor.bind(handlerParam, handle).as(Handler);
            }

        Observer[] observers = [];
        for (MethodInfo methodInfo : observerInfos)
            {
            ObserverMethod methodNext = methodInfo.method.as(ObserverMethod);
            Int            wsidNext   = methodInfo.wsid;
            if (wsidNext != wsid)
                {
                // call to a different service; need to generate a WebService.route() "preamble"
                webService = ensureWebService(wsidNext);
                wsidNext   = wsid;
                handle     = (session, request) -> webService.route(session, request, handle);
                }

            observers = observers.add(methodNext.bindTarget(webService));
            }

        if (observers.size > 0)
            {
            handle = (session, request) ->
                {
                for (Observer observe : observers)
                    {
                    observe^(session, request);
                    }
                return handle(session, request);
                };
            }
        // the chain always starts with a WebService.route() "preamble"
        return (session, request) -> webService.route(session, request, handle);
        }

    /**
     * Collect interceptors for the specified service.
     */
    MethodInfo[] collectInterceptors(Int wsid, HttpMethod httpMethod)
        {
        WebServiceInfo[] services = catalog.services;
        String           path     = services[wsid].path;

        MethodInfo[] interceptors = [];
        for (Int id : 0..wsid)
            {
            WebServiceInfo svc = services[id];
            if (path.startsWith(svc.path))
                {
                svc.interceptors.filter(m -> m.httpMethod == httpMethod, interceptors);
                }
            }
        return interceptors.makeImmutable();
        }

    /**
     * Collect observers for the specified service.
     */
    MethodInfo[] collectObservers(Int wsid, HttpMethod httpMethod)
        {
        WebServiceInfo[] services = catalog.services;
        String           path     = services[wsid].path;

        MethodInfo[] observers = [];
        for (Int id : 0..wsid)
            {
            WebServiceInfo svc = services[id];
            if (path.startsWith(svc.path))
                {
                svc.observers.filter(m -> m.httpMethod == httpMethod, observers);
                }
            }
        return observers.makeImmutable();
        }

    /**
     * Find onError method for the specified service.
     */
    MethodInfo? findOnError(Int wsid)
        {
        WebServiceInfo[] services = catalog.services;
        String           path     = services[wsid].path;

        // the most specific route has the priority
        for (Int id : wsid..0)
            {
            WebServiceInfo svc = services[id];
            if (path.startsWith(svc.path), MethodInfo onError ?= svc.onError)
                {
                return onError;
                }
            }
        return Null;
        }

    private Tuple extractPathValue(Request request, String name, Parameter param, Tuple values)
        {
        Object paramValue;
        if (UriTemplate.Value value := request.matchResult.get(name))
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
            throw new IllegalState($"Missing path parameter: {name.quoted()}");
            }
        return values.add(paramValue.as(param.ParamType));
        }

    private Tuple extractQueryValue(Request request, String name, Parameter param, Tuple values)
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

    private WebService ensureWebService(Int wsid)
        {
        TODO
        }

    private Responder generateResponder(EndpointInfo endpoint)
        {
        if (endpoint.conditionalResult)
            {
            // the method executed is a conditional method that has returned False as the
            // first element of the Tuple
            // TODO: should be handled by "OnError" handler if exists
            return (request, result) -> result[0].as(Boolean)
                       ? createSimpleResponse(endpoint, request, result[1])
                       : TODO new SimpleResponse(HttpStatus.NotFound);
            }

        return (request, result) -> createSimpleResponse(endpoint, request, result[0]);
        }

    Response createSimpleResponse(EndpointInfo endpoint, Request request, Object result)
        {
        AcceptList accepts   = request.accepts;
        MediaType  mediaType = endpoint.resolveResponseContentType(accepts);

        TODO the codec stuff goes here

        TODO return new Response(result, mediaType, codec);
        }
    }