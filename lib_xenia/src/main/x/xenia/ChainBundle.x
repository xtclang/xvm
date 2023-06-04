import Catalog.EndpointInfo;
import Catalog.MethodInfo;
import Catalog.WebServiceInfo;

import web.AcceptList;
import web.Body;
import web.BodyParam;
import web.ErrorHandler;
import web.HttpMethod;
import web.HttpStatus;
import web.MediaType;
import web.ParameterBinding;
import web.QueryParam;
import web.Response;
import web.Session;
import web.UriParam;
import web.WebService;

import web.codecs.Codec;
import web.codecs.Format;
import web.codecs.FormatCodec;
import web.codecs.Registry;
import web.codecs.Utf8Codec;

import net.UriTemplate;

import web.responses.SimpleResponse;


/**
 * The chain bundle represents a set of lazily created call chain collections.
 */
service ChainBundle {
    /**
     * Construct the ChainBundle.
     *
     * @param index  the index of this ChainBundle in the `BundlePool`
     */
    construct(Catalog catalog, Int index) {
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
    Handler ensureCallChain(EndpointInfo endpoint) {
        Handler? handle = chains[endpoint.id];
        if (handle != Null) {
            return handle;
        }

        Int          wsid             = endpoint.wsid;
        HttpMethod   httpMethod       = endpoint.httpMethod;
        MethodInfo[] interceptorInfos = collectInterceptors(wsid, httpMethod);
        MethodInfo[] observerInfos    = collectObservers(wsid, httpMethod);

        Method<WebService> method  = endpoint.method;
        ParameterBinder[]  binders = new ParameterBinder[];

        for (Parameter param : method.params) {
            assert String name := param.hasName();

            if (param.is(ParameterBinding)) {
                String paramName = param.bindName ?: name;

                if (param.is(QueryParam)) {
                    binders += (session, request, values) ->
                        extractQueryValue(request, paramName, param, values);
                    continue;
                }
                if (param.is(UriParam)) {
                    assert endpoint.template.vars.contains(name);

                    binders += (session, request, values) ->
                        extractPathValue(request, name, param, values);
                    continue;
                }
                if (param.is(BodyParam)) {
                    binders += (session, request, values) ->
                        extractBodyValue(request, name, param, values);
                    continue;
                }
                throw new IllegalState($"Unsupported ParameterBinder {param.ParamType}");
            }

            if (endpoint.template.vars.contains(name)) {
                binders += (session, request, values) ->
                    extractPathValue(request, name, param, values);
                continue;
            }

            if (param.ParamType.is(Type<Session>)) {
                binders += (session, request, values) -> values.add(session);
                continue;
            }

            if (param.ParamType == RequestIn) {
                binders += (session, request, values) -> values.add(request);
                continue;
            }

            if (param.ParamType defaultValue := param.defaultValue()) {
                binders += (session, request, values) -> values.add(defaultValue);
                continue;
            }

            throw new IllegalState($"Unresolved parameter: {name.quoted()} for method {method}");
        }

        typedef Method<WebService, <Session, RequestIn, Handler>, <ResponseOut>> as InterceptorMethod;
        typedef Method<WebService, <Session, RequestIn>, <>>                     as ObserverMethod;

        // start with the innermost endpoint
        WebService webService  = ensureWebService(wsid);
        Function   boundMethod = method.bindTarget(webService);
        Responder  respond     = generateResponder(endpoint);

        handle = binders.empty
            ? ((session, request) -> respond(request, boundMethod()))
            : ((session, request) -> {
                Tuple values = Tuple:();
                for (ParameterBinder bind : binders) {
                    values = bind(session, request, values);
                }
                return respond(request, boundMethod.invoke(values));
            });

        for (MethodInfo info : interceptorInfos) {
            Method methodNext = info.method;
            Int    wsidNext   = info.wsid;
            if (wsidNext != wsid) {
                // call to a different service; need to generate a WebService.route() "preamble"
                // even if the WebService doesn't have any interceptors (it that case it must have
                // an error handler or an explicitly defined "route()" method)
                ErrorHandler? onError = ensureErrorHandler(wsid);

                Handler callNext = handle;
                handle = (session, request) -> webService.route(session, request, callNext, onError);

                webService = ensureWebService(wsidNext);
                wsid       = wsidNext;
            }

            if (methodNext.is(InterceptorMethod)) {
                Interceptor        interceptor  = methodNext.bindTarget(webService);
                Parameter<Handler> handlerParam = interceptor.params[2].as(Parameter<Handler>);

                handle = interceptor.bind(handlerParam, handle).as(Handler);
            }
        }

        Int observerCount = observerInfos.size;
        if (observerCount > 0) {
            Observer[] observers = new Observer[observerCount] (i -> {
                    MethodInfo info = observerInfos[i];
                    return info.method.as(ObserverMethod).bindTarget(ensureWebService(info.wsid));
            });

            Handler callNext = handle;
            handle = (session, request) -> {
                // observers are not handlers and call asynchronously
                for (Observer observe : observers) {
                    observe^(session, request);
                }
                return callNext(session, request);
            };
        }

        // the chain always starts with a WebService.route() "preamble"
        ErrorHandler? onError = ensureErrorHandler(wsid);

        Handler callNext = handle;
        handle = (session, request) -> {
            assert session.is(SessionImpl);
            session.requestBegin_(request);
            try {
                return webService.route(session, request, callNext, onError);
            } finally {
                session.requestEnd_(request);
            }
        };

        chains[endpoint.id] = handle.makeImmutable();
        return handle;
    }

    /**
     * Collect interceptors for the specified service. Note, that if a WebService in the path
     * doesn't have any interceptors, but has an explicitly defined "route" method or an error
     * handler, we still need to include it in the list.
     */
    private MethodInfo[] collectInterceptors(Int wsid, HttpMethod httpMethod) {
        WebServiceInfo[] serviceInfos = catalog.services;
        String           path         = serviceInfos[wsid].path;

        MethodInfo[] interceptors = [];
        for (Int id : 0..wsid) {
            WebServiceInfo serviceInfo = serviceInfos[id];
            if (path.startsWith(serviceInfo.path)) {
                if (serviceInfo.interceptors.empty) {
                    if (MethodInfo onErrorInfo ?= serviceInfo.onError) {
                        interceptors.add(onErrorInfo);
                    }
                    if (MethodInfo routeInfo ?= serviceInfo.route) {
                        interceptors.add(routeInfo);
                    }
                } else {
                    serviceInfo.interceptors.filter(m -> m.httpMethod == httpMethod, interceptors);
                }
            }
        }
        return interceptors.makeImmutable();
    }

    /**
     * Collect observers for the specified WebService.
     */
    private MethodInfo[] collectObservers(Int wsid, HttpMethod httpMethod) {
        WebServiceInfo[] serviceInfos = catalog.services;
        String           path         = serviceInfos[wsid].path;

        MethodInfo[] observerInfos = [];
        for (Int id : 0..wsid) {
            WebServiceInfo serviceInfo = serviceInfos[id];
            if (path.startsWith(serviceInfo.path)) {
                serviceInfo.observers.filter(m -> m.httpMethod == httpMethod, observerInfos);
            }
        }
        return observerInfos.makeImmutable();
    }

    /**
     * Create an error handler for the specified WebService.
     */
     ErrorHandler? ensureErrorHandler(Int wsid) {
        typedef Method<WebService, <Session, RequestIn, Exception|String|HttpStatus>, <ResponseOut>>
                as ErrorMethod;

        if (ErrorHandler onError ?= errorHandlers[wsid]) {
            return onError;
        }

        MethodInfo? onErrorInfo = catalog.services[wsid].onError;
        if (onErrorInfo == Null) {
            return Null;
        }

        ErrorHandler onError = onErrorInfo.method.as(ErrorMethod).bindTarget(ensureWebService(wsid));
        errorHandlers[wsid] = onError;
        return onError;
    }

    /**
     * Ensure a WebService instance for the specified id.
     */
    WebService ensureWebService(Int wsid) {
        WebService? svc = services[wsid];
        if (svc != Null) {
            return svc;
        }

        svc = catalog.services[wsid].constructor();
        svc.webApp = catalog.webApp;
        services[wsid] = svc;
        return svc;
    }

    /**
     * Extract the path value from the request and append it to the values Tuple.
     */
    private Tuple extractPathValue(RequestIn request, String path, Parameter param, Tuple values) {
        Object paramValue;
        if (UriTemplate.Value value := request.matchResult.get(path)) {
            paramValue = convertValue(value, param.ParamType, param.is(UriParam) ? param.format : Null);
        } else if (param.ParamType defaultValue := param.defaultValue()) {
            paramValue = defaultValue;
        } else {
            throw new IllegalState($"Missing path parameter: {path.quoted()}");
        }
        return values.add(paramValue.as(param.ParamType));
    }

    /**
     * Extract the query value from the request and append it to the values Tuple.
     */
    private Tuple extractQueryValue(RequestIn request, String name, QueryParam param, Tuple values) {
        Object paramValue;
        if (UriTemplate.Value value := request.queryParams.get(name)) {
            paramValue = convertValue(value, param.ParamType, param.format);
        } else if (param.ParamType defaultValue := param.defaultValue()) {
            paramValue = defaultValue;
        } else {
            throw new IllegalState($"Missing query parameter: {name.quoted}");
        }
        return values.add(paramValue.as(param.ParamType));
    }

    /**
     * Convert the specified value into the specified type using the specified format (optional).
     */
    private Object convertValue(UriTemplate.Value value, Type type, String? formatName) {
        if (&value.actualType.isA(type)) {
            return value;
        }

        Format<type.DataType> format;
        if (formatName == Null) {
            if (!(format := registry.findFormatByType(type))) {
                throw new IllegalState($|Unsupported type: "{type}"
                                      );
            }
        } else {
            if (!(format := registry.findFormat(formatName, type))) {
                throw new IllegalState($|Unsupported format: "{formatName}" for type "{type}"
                                      );
            }
        }

        if (value.is(String)) {
            return format.decode(value);
        }

        // List<String> | Map<String, String
        TODO
    }

    /**
     * Extract the body value from the request and append it to the values Tuple.
     */
    private Tuple extractBodyValue(RequestIn request, String name, BodyParam param, Tuple values) {
        Body? body = request.body;
        if (body == Null) {
            throw new IllegalState($"Request has no body");
        }

        Object paramValue;
        switch (param.ParamType.is(_)) {
        case Type<Byte[]>:
            paramValue = body.bytes;
            break;

        default:
            Type type = param.ParamType;
            if (Codec<type.DataType> codec := registry.findCodec(body.mediaType, type)) {
                paramValue = codec.decode(body.bytes);
                break;
            }

            throw new IllegalState($"Unsupported BodyParam type: \"{param.ParamType}\"");
        }

        return values.add(paramValue.as(param.ParamType));
    }

    /**
     * Generate a response handler for the specified endpoint.
     *
     * Note: Responders we return don't need to "freeze" the response they produce; it will be done
     *       by the WebService.route() method.
     */
    private Responder generateResponder(EndpointInfo endpoint) {
        import ecstasy.reflect.Return;

        Return[] returns = endpoint.method.returns;
        if (returns.size == 0) {
            return (request, result) -> new SimpleResponse(OK);
        }

        // if the method is conditional, the element zero of the tuple is the Boolean value
        Int  index = endpoint.conditionalResult ? 1 : 0;
        Type type  = returns[index].ReturnType;

        // check for special return types
        switch (type.is(_), index) {
        case (Type<ResponseOut>, 0):
            return (request, result) -> result[0].as(ResponseOut);

        case (Type<ResponseOut>, 1):
            return (request, result) ->
                (result[0].as(Boolean)
                    ? result[1].as(ResponseOut)
                    : new SimpleResponse(HttpStatus.NotFound));

        case (Type<HttpStatus>, 0):
            return (request, result) ->
                new SimpleResponse(result[0].as(HttpStatus));

        case (Type<HttpStatus>, 1):
            return (request, result) ->
                new SimpleResponse(result[0].as(Boolean)
                                    ? result[1].as(HttpStatus)
                                    : HttpStatus.NotFound);
        }

        // helper function to look up a Codec based on the result type and the MediaType
        Codec findCodec(MediaType mediaType, Type type) {
            if (Codec codec := registry.findCodec(mediaType, type)) {
                return codec;
            }

            throw new IllegalState($"Unsupported mediaType {mediaType} type {type}");
        }

        MediaType|MediaType[] produces = endpoint.produces;
        if (produces == []) {
            produces = Json;
        }
        if (produces.is(MediaType)) {
            MediaType mediaType = produces;
            Codec     codec     = findCodec(mediaType, type);

            if (index == 0) {
                return (request, result) ->
                    createSimpleResponse(mediaType, codec, request, result[0]);
            }

            return (request, result) ->
                result[0].as(Boolean)
                   ? createSimpleResponse(mediaType, codec, request, result[1])
                   : new SimpleResponse(HttpStatus.NotFound);
        } else {
            MediaType[] mediaTypes = produces;
            Codec[]     codecs     = new Codec[mediaTypes.size] (i -> findCodec(mediaTypes[i], type));

            if (index == 0) {
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
    private static ResponseOut createSimpleResponse(
            MediaType mediaType, Codec codec, RequestIn request, Object result) {
        if (!request.accepts.matches(mediaType)) {
            TODO find a converter and convert
        }

        return new SimpleResponse(HttpStatus.OK, mediaType, codec.encode(result));
    }

    /**
     * Create an HTTP response for a multi-media producer.
     */
    private static ResponseOut createSimpleResponse(
            MediaType[] mediaTypes, Codec[] codecs, RequestIn request, Object result) {
        (MediaType, Codec)
                resolveContentType(MediaType[] mediaTypes, Codec[] codecs, AcceptList accepts) {
            Loop:
            for (MediaType mediaType : mediaTypes) {
                if (accepts.matches(mediaType)) {
                    return mediaType, codecs[Loop.count];
                }
            }

            TODO find a converter and convert
        }

        (MediaType mediaType, Codec codec) = resolveContentType(mediaTypes, codecs, request.accepts);

        return new SimpleResponse(HttpStatus.OK, mediaType, codec.encode(result));
    }
}