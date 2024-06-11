import Catalog.EndpointInfo;
import Catalog.MethodInfo;
import Catalog.WebServiceInfo;

import convert.Codec;
import convert.Format;
import convert.codecs.FormatCodec;
import convert.codecs.Utf8Codec;

import net.UriTemplate;

import sec.Entity;
import sec.Permission;
import sec.Realm;

import web.AcceptList;
import web.Body;
import web.BodyParam;
import web.ErrorHandler;
import web.HttpMethod;
import web.HttpStatus;
import web.MediaType;
import web.ParameterBinding;
import web.QueryParam;
import web.Registry;
import web.Response;
import web.TrustLevel;
import web.UriParam;

import web.responses.SimpleResponse;

import web.security.Authenticator;


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
     * A function that adds a permission check to an endpoint call. It returns `False` if the
     * endpoint invocation is not restricted and allowed to proceed. Otherwise, it returns a
     * (conditional) [ResponseOut] indicatung a failure.
     */
    typedef function conditional ResponseOut(RequestIn) as RestrictionCheck;

    /**
     * A function that adds a parameter value to the passed-in tuple of values. Used to collect
     * arguments for the endpoint method invocation.
     */
    typedef function Tuple(RequestIn, Tuple) as ParameterBinder;

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
    Handler ensureCallChain(EndpointInfo endpoint, Authenticator authenticator) {
        Handler? handle = chains[endpoint.id];
        if (handle != Null) {
            return handle;
        }

        Int          wsid             = endpoint.wsid;
        HttpMethod   httpMethod       = endpoint.httpMethod;
        WebService   webService       = ensureWebService(wsid);
        MethodInfo[] interceptorInfos = collectInterceptors(wsid, httpMethod);
        MethodInfo[] observerInfos    = collectObservers(wsid, httpMethod);

        // bind the restriction (permission) check
        RestrictionCheck? restrictCheck = generateRestrictCheck(endpoint, authenticator, webService);

        // bind the parameters
        Method<WebService> method  = endpoint.method;
        ParameterBinder[]  binders = new ParameterBinder[];

        for (Parameter param : method.params) {
            assert String name := param.hasName();

            if (param.is(ParameterBinding)) {
                name ?= param.bindName;

                if (param.is(QueryParam)) {
                    binders += (request, values) ->
                        extractQueryValue(request, name, param, values);
                    continue;
                }
                if (param.is(UriParam)) {
                    assert endpoint.template.vars.contains(name);

                    binders += (request, values) ->
                        extractPathValue(request, name, param, values);
                    continue;
                }
                if (param.is(BodyParam)) {
                    binders += (request, values) ->
                        extractBodyValue(request, name, param, values);
                    continue;
                }
                throw new IllegalState($"Unsupported ParameterBinder {param.ParamType}");
            }

            if (endpoint.template.vars.contains(name)) {
                binders += (request, values) ->
                    extractPathValue(request, name, param, values);
                continue;
            }

            if (param.ParamType.is(Type<Session>)) {
                binders += (request, values) -> values.add(request.session? : assert);
                continue;
            }

            if (param.ParamType == RequestIn) {
                binders += (request, values) -> values.add(request);
                continue;
            }

            if (param.ParamType defaultValue := param.defaultValue()) {
                binders += (request, values) -> values.add(defaultValue);
                continue;
            }

            throw new IllegalState($"Unresolved parameter: {name.quoted()} for method {method}");
        }

        typedef Method<WebService, <RequestIn, Handler>, <ResponseOut>> as InterceptorMethod;
        typedef Method<WebService, <RequestIn>, <>>                     as ObserverMethod;

        // start with the innermost endpoint
        Function  boundMethod = method.bindTarget(webService);
        Responder respond     = generateResponder(endpoint);

        handle = binders.empty
            ? ((request) -> respond(request, boundMethod()))
            : ((request) -> {
                Tuple values = Tuple:();
                for (ParameterBinder bind : binders) {
                    values = bind(request, values);
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
                handle = (request) -> webService.route(request, callNext, onError);

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
            Observer[] observers = new Observer[observerCount](i -> {
                    MethodInfo info = observerInfos[i];
                    return info.method.as(ObserverMethod).bindTarget(ensureWebService(info.wsid));
            });

            Handler callNext = handle;
            handle = (request) -> {
                // observers are not handlers and call asynchronously
                for (Observer observe : observers) {
                    observe^(request);
                }
                return callNext(request);
            };
        }

        // the chain always starts with a WebService.route() "preamble"
        ErrorHandler? onError = ensureErrorHandler(wsid);

        Handler callNext = handle;
        handle = (request) -> {
            SessionImpl? session = request.session.is(SessionImpl) ?: Null;
            session?.requestBegin_(request);
            try {
                return webService.route(request, callNext, onError);
            } finally {
                session?.requestEnd_(request);
            }
        };

        // but nothing can get through unless the permission is checked
        if (restrictCheck != Null) {
            callNext = handle;
            handle   = (request) -> {
                if (ResponseOut failure := restrictCheck(request)) {
                    return failure;
                }
                return callNext(request);
            };
        }

        chains[endpoint.id] = handle.makeImmutable();
        return handle;
    }

    private RestrictionCheck? generateRestrictCheck(EndpointInfo endpoint,
                                                    Authenticator authenticator, WebService webService) {
        import Catalog.Restriction;

        TrustLevel  requiredTrust      = endpoint.requiredTrust;
        Restriction requiredPermission = endpoint.requiredPermission;

        if (requiredTrust == None || requiredPermission == Null) {
            // no permission check is required
            return Null;
        }

        (function Permission(RequestIn))? resolvePermission = Null;
        (function Boolean())?             checkPermission   = Null;

        if (requiredPermission.is(Permission)) {
            resolvePermission = (_) -> requiredPermission;
        } else if (requiredPermission.is(function Permission(RequestIn))) {
            resolvePermission = requiredPermission;
        } else {
            // Method<WebService, <>, <Boolean>>
            checkPermission = requiredPermission.bindTarget(webService).as((function Boolean()));
            // TODO: bind necessary arguments
        }

        return (request) -> {
            // each endpoint has a required trust level, and the session knows its own trust
            // level; if the endpoint requirement is higher, then we have to re-authenticate
            // the user agent; the server must respond in a manner that causes the client to
            // authenticate, including (but not limited to) any of the following manners:
            // * with the `Unauthorized` error code (and related information) that indicates
            //   that the client must authenticate itself
            // * with a redirect to a URL that provides the necessary login user interface
            Session? session = request.session;
            if (session == Null || requiredTrust <= session.trustLevel) {
                return False;
            }

            import Authenticator.Attempt;
            import Authenticator.Status as AuthStatus;

            Attempt[] attempts = authenticator.authenticate(request);
            Entity[]  entities = [];
            if (attempts[0].status == Success) {
//                TODO CP
//                    switch (attempts[0].response) {
//                    case Allowed:
//                        // Authenticator has verified that the user is authenticated (the
//                        // Authenticator should have already updated the session accordingly)
//                        if (endpoint.requiredTrust > session.trustLevel) {
//                            // the user is authenticated, but the user doesn't have the
//                            // necessary security access
//                            return True, new SimpleResponse(Forbidden);
//                        }
//
//                        // the user is authenticated and has the necessary security access;
//                        // continue processing the request
//                        break;
//
//                    case Unknown:
//                        // the request didn't have any authorization information or the
//                        // authorizer didn't know how to process it
//                        return True, new SimpleResponse(Unauthorized);
//
//                    case Forbidden:
//                        // authentication didn't just fail, but it has been disallowed; respond
//                        // with an HTTP "Forbidden"
//                        return True, new SimpleResponse(Forbidden);
//                    }
            } else {
                  // "authResult" is actually an HTTP response to send back to the client to
                  // take the next step in the authentication process
                  return True, attempts[0].response.as(ResponseOut);
            }

            if (resolvePermission != Null) {
                // resolve the required endpoint permission and verify that at least one of the
                // authenticated entities is permitted to invoke the endpoint
                Permission permission = resolvePermission(request);
                Realm      realm      = authenticator.realm;
                for (Entity entity : entities) {
                    if (entity.permitted(realm, permission)) {
                        return False;
                    }
                }
            } else {
                if (checkPermission?()) {
                    return False;
                }
            }

        // the request doesn't have the necessary security permissions
        return True, new SimpleResponse(Forbidden);
        };
    }

    /**
     * Collect interceptors for the specified service. Note, that if a WebService in the path
     * doesn't have any interceptors, but has an explicitly defined "route" method or an error
     * handler, we still need to include it in the list.
     */
    private MethodInfo[] collectInterceptors(Int wsid, HttpMethod httpMethod) {
        WebServiceInfo[] serviceInfos = catalog.services;
        String           path         = serviceInfos[wsid].path;

        MethodInfo[] interceptors = new MethodInfo[];
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
                    interceptors.addAll(serviceInfo.interceptors.filter(m -> m.httpMethod == httpMethod));
                }
            }
        }
        return interceptors.freeze(inPlace=True);
    }

    /**
     * Collect observers for the specified WebService.
     */
    private MethodInfo[] collectObservers(Int wsid, HttpMethod httpMethod) {
        WebServiceInfo[] serviceInfos = catalog.services;
        String           path         = serviceInfos[wsid].path;

        MethodInfo[] observerInfos = new MethodInfo[];
        for (Int id : 0..wsid) {
            WebServiceInfo serviceInfo = serviceInfos[id];
            if (path.startsWith(serviceInfo.path)) {
                observerInfos.addAll(serviceInfo.observers.filter(m -> m.httpMethod == httpMethod));
            }
        }
        return observerInfos.freeze(inPlace=True);
    }

    /**
     * Create an error handler for the specified WebService.
     */
     ErrorHandler? ensureErrorHandler(Int wsid) {
        typedef Method<WebService, <RequestIn, Exception|String|HttpStatus>, <ResponseOut>>
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
            throw new IllegalState($"Missing query parameter: {name.quoted()}");
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
        Body?  body = request.body;
        Object paramValue;
        if (body == Null) {
            if (!(paramValue := param.defaultValue())) {
                throw new IllegalState($"Request has no body");
            }
        } else {
            Type   paramType = param.ParamType;
            switch (paramType.is(_)) {
            case Type<Byte[]>:
                paramValue = body.bytes;
                break;

            default:
                if (Codec<paramType.DataType> codec := registry.findCodec(body.mediaType, paramType)) {
                    paramValue = codec.decode(body.bytes);
                    if (String formatName ?= param.format) {
                        if (Format format := registry.findFormat(formatName, paramType),
                                   paramValue.is(String)) {
                            paramValue = format.decode(paramValue);
                            break;
                        }
                    } else {
                        break;
                    }
                }
                throw new IllegalState($"Unsupported BodyParam type: \"{paramType}\"");
            }
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
        Boolean  cond    = endpoint.conditionalResult;
        Type     type;

        switch (returns.size) {
        case 0:
            return (request, result) -> new SimpleResponse(OK);

        case 1:
            type = returns[0].ReturnType;
            break;

        case 2:
            if (cond) {
                type = returns[1].ReturnType;
                break;
            }
            continue;

        default:
            throw new IllegalState($|Method "{endpoint.method}": \
                                    |multiple returns are not currently supported
                                    );
        }

        // check for special return types (`HttpStatus` or `ResponseOut`)
        switch (type.is(_), cond) {
        case (Type<ResponseOut>, False):
            return (request, result) -> result[0].as(ResponseOut);

        case (Type<ResponseOut>, True):
            return (request, result) ->
                (result[0].as(Boolean)
                    ? result[1].as(ResponseOut)
                    : new SimpleResponse(HttpStatus.NotFound));

        case (Type<HttpStatus>, False):
            return (request, result) ->
                new SimpleResponse(result[0].as(HttpStatus));

        case (Type<HttpStatus>, True):
            return (request, result) ->
                new SimpleResponse(result[0].as(Boolean)
                                    ? result[1].as(HttpStatus)
                                    : HttpStatus.NotFound);
        }

        // check for a union type where one type is `ResponseOut`
        Boolean union = False;
        if (type.form == Union) {
            if (cond) {
                throw new IllegalState($|Method "{endpoint.method}": \
                                        |conditional return of a union type is not currently supported
                                        );

            }
            union = True;
            assert (Type t1, Type t2) := type.relational();
            if        (t1.is(Type<ResponseOut>)) {
                type = t2;
            } else if (t2.is(Type<ResponseOut>)) {
                type = t1;
            } else {
                throw new IllegalState($|Method "{endpoint.method}": \
                                        |arbitrary union types are not currently supported
                                        );
            }
        }

        // helper function to look up a Codec based on the result type and the MediaType
        Codec findCodec(EndpointInfo endpoint, MediaType mediaType, Type type) {
            if (Codec codec := registry.findCodec(mediaType, type)) {
                return codec;
            }
            throw new IllegalState($|Method "{endpoint.method}": \
                                    |unsupported mediaType "{mediaType}" for type "{type}"
                                    );
        }

        MediaType|MediaType[] produces = endpoint.produces;
        if (produces == []) {
            produces = Json;
        }
        if (produces.is(MediaType)) {
            MediaType mediaType = produces;
            Codec     codec     = findCodec(endpoint, mediaType, type);

            if (cond) {
                return (request, result) ->
                    result[0].as(Boolean)
                       ? createSimpleResponse(mediaType, codec, request, result[1])
                       : new SimpleResponse(HttpStatus.NotFound);
            } else if (union) {
                return (request, result) -> {
                    if (ResponseOut response := result[0].is(ResponseOut)) {
                        return response;
                    } else {
                        return createSimpleResponse(mediaType, codec, request, result[0]);
                    }};
            } else {
                return (request, result) ->
                    createSimpleResponse(mediaType, codec, request, result[0]);
            }

        } else {
            MediaType[] mediaTypes = produces;
            Codec[]     codecs     = new Codec[mediaTypes.size](i -> findCodec(endpoint, mediaTypes[i], type));

            if (cond) {
                return (request, result) ->
                    result[0].as(Boolean)
                       ? createSimpleResponse(mediaTypes, codecs, request, result[1])
                       : new SimpleResponse(HttpStatus.NotFound);
            } else if (union) {
                return (request, result) -> {
                    if (ResponseOut response := result[0].is(ResponseOut)) {
                        return response;
                    } else {
                        return createSimpleResponse(mediaTypes, codecs, request, result[0]);
                    }};
            } else {
                return (request, result) ->
                    createSimpleResponse(mediaTypes, codecs, request, result[0]);
            }
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

        (MediaType, Codec) resolveContentType(
                MediaType[] mediaTypes, Codec[] codecs, AcceptList accepts) {

            Loop: for (MediaType mediaType : mediaTypes) {
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