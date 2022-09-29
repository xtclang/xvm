import web.AcceptList;
import web.ErrorHandler;
import web.HttpMethod;
import web.HttpStatus;
import web.MediaType;
import web.ParameterBinding;
import web.UriParam;
import web.QueryParam;
import web.Response;
import web.Session;
import web.WebService;

import web.responses.SimpleResponse;

import web.routing.Catalog.EndpointInfo;
import web.routing.Catalog.MethodInfo;
import web.routing.Catalog.WebServiceInfo;
import web.routing.UriTemplate;


/**
 * The chain bundle represents a set of lazily created call chain collections.
 */
service ChainBundle
    {
    construct(Catalog catalog, Int index)
        {
        this.catalog = catalog;
        this.index   = index;

        services      = new WebService?[catalog.serviceCount];
        chains        = new Handler?[catalog.endpointCount];
        errorHandlers = new ErrorHandler?[catalog.serviceCount];
        }

    /**
     * The Catalog.
     */
    public/private Catalog catalog;

    /**
     * The index of this bundle in the BundlePool.
     */
    public/private Int index;

    /**
     * The WebServices indexed by the WebService id.
     */
    protected WebService?[] services;

    /**
     * The CallChains indexed by the endpoint id.
     */
    protected Handler?[] chains;

    /**
     * The ErrorHandlers indexed by the WebService id.
     */
    protected ErrorHandler?[] errorHandlers;

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
                String paramName = param.bindName ?: name;

                if (param.is(QueryParam))
                    {
                    binders += (session, request, values) ->
                        extractQueryValue(request, paramName, param, values);
                    continue;
                    }
                if (param.is(UriParam))
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

            if (param.ParamType.is(Type<Session>))
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
                // even if the WebService doesn't have any interceptors (it that case it must have
                // an error handler or an explicitly defined "route()" method)
                ErrorHandler? onError = ensureErrorHandler(wsid);

                Handler callNext = handle;
                handle = (session, request) -> webService.route(session, request, callNext, onError);

                webService = ensureWebService(wsidNext);
                wsid       = wsidNext;
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

            Handler callNext = handle;
            handle = (session, request) ->
                {
                // observers are not handlers and call asynchronously
                for (Observer observe : observers)
                    {
                    observe^(session, request);
                    }
                return callNext(session, request);
                };
            }

        // the chain always starts with a WebService.route() "preamble"
        ErrorHandler? onError = ensureErrorHandler(wsid);

        Handler callNext = handle;
        handle = (session, request) -> webService.route(session, request, callNext, onError);

        chains[endpoint.id] = handle;
        return handle;
        }

    /**
     * Collect interceptors for the specified service. Note, that if a WebService in the path
     * doesn't have any interceptors, but has an explicitly defined "route" method or an error
     * handler, we still need to include it in the list.
     */
    private MethodInfo[] collectInterceptors(Int wsid, HttpMethod httpMethod)
        {
        WebServiceInfo[] serviceInfos = catalog.services;
        String           path         = serviceInfos[wsid].path;

        MethodInfo[] interceptors = [];
        for (Int id : 0..wsid)
            {
            WebServiceInfo serviceInfo = serviceInfos[id];
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
    private MethodInfo[] collectObservers(Int wsid, HttpMethod httpMethod)
        {
        WebServiceInfo[] serviceInfos = catalog.services;
        String           path         = serviceInfos[wsid].path;

        MethodInfo[] observerInfos = [];
        for (Int id : 0..wsid)
            {
            WebServiceInfo serviceInfo = serviceInfos[id];
            if (path.startsWith(serviceInfo.path))
                {
                serviceInfo.observers.filter(m -> m.httpMethod == httpMethod, observerInfos);
                }
            }
        return observerInfos.makeImmutable();
        }

    /**
     * Create an error handler for the specified WebService.
     */
     ErrorHandler? ensureErrorHandler(Int wsid)
        {
        typedef Method<WebService, <Session, Request, Exception|String|HttpStatus>, <Response>>
                as ErrorMethod;

        if (ErrorHandler onError ?= errorHandlers[wsid])
            {
            return onError;
            }

        MethodInfo? onErrorInfo = catalog.services[wsid].onError;
        if (onErrorInfo == Null)
            {
            return Null;
            }

        ErrorHandler onError = onErrorInfo.method.as(ErrorMethod).bindTarget(ensureWebService(wsid));
        errorHandlers[wsid] = onError;
        return onError;
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
        svc.webApp = catalog.webApp;
        services[wsid] = svc;
        return svc;
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
            // TODO:
            // Format format ?:= registry.giveMeAFormatByType(paramType)
            // f = format.convert
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
     * Generate a response handler for the specified endpoint.
     */
    private static Responder generateResponder(EndpointInfo endpoint)
        {
        if (endpoint.conditionalResult)
            {
            // the method executed is a conditional method that has returned False as the
            // first element of the Tuple
            return (request, result) -> result[0].as(Boolean)
                   ? createSimpleResponse(endpoint, request, result[1])
                   : new SimpleResponse(HttpStatus.NotFound).makeImmutable();
            }

        return (request, result) -> createSimpleResponse(endpoint, request,
                                        result.size == 0 ? HttpStatus.OK : result[0]);
        }

    /**
     * Create an HTTP response.
     */
    private static Response createSimpleResponse(EndpointInfo endpoint, Request request, Object result)
        {
        if (result.is(Response))
            {
            return result;
            }

        AcceptList accepts   = request.accepts;
        MediaType  mediaType = endpoint.resolveResponseContentType(accepts);

        if (result.is(HttpStatus))
            {
            return new SimpleResponse(result, mediaType, []).makeImmutable();
            }

        // TODO: replace the code below with a codec/format call

        assert mediaType == Json as $"Not supported media type {mediaType}";

        import ecstasy.io.ByteArrayOutputStream;
        import ecstasy.io.UTF8Writer;

        import json.Doc;
        import json.Schema;

        Byte[] body;
        switch (result.is(_))
            {
            case Array<Byte>: // TODO CP: "case Byte[]" dosn't parse
                body = result;
                break;

            case String:
                body = result.quoted().utf8();
                break;

            case Doc: // TODO GG: typedef doesn't resolve: try "case json.Doc:"
                body = json.Printer.DEFAULT.render(result).utf8();
                break;

            default:
                ByteArrayOutputStream out  = new ByteArrayOutputStream();
                Type                  type = &result.actualType;

                Schema.DEFAULT.createObjectOutput(new UTF8Writer(out)).
                        write(result.as(type.DataType));

                body = out.bytes;
                break;
            }

        return new SimpleResponse(HttpStatus.OK, mediaType, body).makeImmutable();
        }
    }