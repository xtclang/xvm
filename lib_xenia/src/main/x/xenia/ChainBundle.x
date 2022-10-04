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

import web.codecs.Codec;
import web.codecs.Format;
import web.codecs.FormatCodec;
import web.codecs.Registry;
import web.codecs.Utf8Codec;

import web.routing.Catalog.EndpointInfo;
import web.routing.Catalog.MethodInfo;
import web.routing.Catalog.WebServiceInfo;
import web.routing.UriTemplate;

import web.responses.SimpleResponse;


/**
 * The chain bundle represents a set of lazily created call chain collections.
 */
service ChainBundle
    {
    /**
     * Construct the ChainBundle.
     *
     * @param index  the index of this ChainBundle in the `BundlePool`
     */
    construct(Catalog catalog, Int index)
        {
        this.catalog = catalog;
        this.index   = index;

        registry      = catalog.webApp.registry_;
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
     * The Registry.
     */
    protected Registry registry;

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

        chains[endpoint.id] = handle.makeImmutable();
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
    private Responder generateResponder(EndpointInfo endpoint)
        {
        import ecstasy.reflect.Return;

        Return[] returns = endpoint.method.returns;
        if (returns.size == 0)
            {
            return (request, result) -> new SimpleResponse(OK);
            }

        // if the method is conditional, the element zero of the tuple is the Boolean value
        Int  index = endpoint.conditionalResult ? 1 : 0;
        Type type  = returns[index].ReturnType;

        // check for special return types
        switch (type.is(_), index)
            {
            case (Type<Response>, 0):
                return (request, result) -> result[0].as(Response);

            case (Type<Response>, 1):
                return (request, result) ->
                    (result[0].as(Boolean)
                        ? result[1].as(Response)
                        : new SimpleResponse(HttpStatus.NotFound)
                    );

            case (Type<HttpStatus>, 0):
                return (request, result) ->
                    new SimpleResponse(result[0].as(HttpStatus));

            case (Type<HttpStatus>, 1):
                return (request, result) ->
                    new SimpleResponse(result[0].as(Boolean)
                                        ? result[1].as(HttpStatus)
                                        : HttpStatus.NotFound
                                      );
            }

        // helper function to look up a Codec based on the result type and the MediaType
        Codec findCodec(MediaType mediaType, Type type)
            {
            if (String formatName ?= mediaType.format)
                {
                if (Format<type.DataType> format := registry.findFormat(formatName, type.DataType))
                    {
                    return new FormatCodec<type.DataType>(Utf8Codec, format);
                    }

                throw new IllegalState($"Unsupported mediaType format: {formatName}");
                }
            else
                {
                if (Codec codec := registry.findCodec(mediaType, type.DataType))
                    {
                    return codec;
                    }

                throw new IllegalState($"Unsupported mediaType: {mediaType}");
                }
            }

        MediaType|MediaType[] produces = endpoint.produces;
        if (produces == [])
            {
            produces = Json;
            }
        if (produces.is(MediaType))
            {
            MediaType mediaType = produces;
            Codec     codec     = findCodec(mediaType, type);

            if (index == 0)
                {
                return (request, result) ->
                    createSimpleResponse(mediaType, codec, request, result[0]);
                }

            return (request, result) ->
                result[0].as(Boolean)
                   ? createSimpleResponse(mediaType, codec, request, result[1])
                   : new SimpleResponse(HttpStatus.NotFound);
            }
        else
            {
            MediaType[] mediaTypes = produces;
            Codec[]     codecs     = new Codec[mediaTypes.size] (i -> findCodec(mediaTypes[i], type));

            if (index == 0)
                {
                return (request, result) ->
                    createSimpleResponse(mediaTypes, codecs, request, result[0]);
                }

            return (request, result) ->
                result[0].as(Boolean)
                   ? createSimpleResponse(mediaTypes, codecs, request, result[1])
                   : new SimpleResponse(HttpStatus.NotFound);
            }
        }

    /**
     * Create an HTTP response for a single-media producer.
     */
    private static Response createSimpleResponse(
            MediaType mediaType, Codec codec, Request request, Object result)
        {
        if (!request.accepts.matches(mediaType))
            {
            TODO find a converter and convert
            }

        return new SimpleResponse(HttpStatus.OK, mediaType, codec.encode(result));
        }

    /**
     * Create an HTTP response for a multi-media producer.
     */
    private static Response createSimpleResponse(
            MediaType[] mediaTypes, Codec[] codecs, Request request, Object result)
        {
        (MediaType, Codec)
                resolveContentType(MediaType[] mediaTypes, Codec[] codecs, AcceptList accepts)
            {
            Loop:
            for (MediaType mediaType : mediaTypes)
                {
                if (accepts.matches(mediaType))
                    {
                    return mediaType, codecs[Loop.count];
                    }
                }

            TODO find a converter and convert
            }

        (MediaType mediaType, Codec codec) = resolveContentType(mediaTypes, codecs, request.accepts);

        return new SimpleResponse(HttpStatus.OK, mediaType, codec.encode(result));
        }
    }