import ecstasy.reflect.AnnotationTemplate;
import ecstasy.reflect.Argument;

import net.UriTemplate;
import net.UriTemplate.UriParameters;

import sec.Permission;

import web.*;
import web.security.Authenticator;
import web.sessions.Broker;

import WebService.Constructor as WSConstructor;
import WebService.ExtrasAware;

/**
 * The catalog of WebApp endpoints.
 */
const Catalog(WebApp webApp, WebServiceInfo[] services, Class[] sessionAnnos) {

    /**
     * The Restriction could be one of:
     *  - Null, indicating an absence of any restrictions;
     *  - a static Permission object computed at Catalog creation time;
     *  - a function that creates a Permission object at request time;
     *  - a user defined method called at request time
     */
    typedef Permission? | function Permission(RequestIn) | Method<WebService, <>, <Boolean>>
        as Restriction;

    /**
     * The list of [WebServiceInfo] objects describing the [WebService] classes discovered within
     * the application.
     */
    WebServiceInfo[] services;

    /**
     * The list of [Session] annotation classes discovered within the application.
     */
    Class[] sessionAnnos;

    /**
     * The total number of WebServices.
     */
    Int serviceCount.get() {
        return services.size;
    }

    /**
     * The total number of endpoints.
     */
    @Lazy Int endpointCount.calc() {
        return services.map(WebServiceInfo.endpointCount).reduce(new aggregate.Sum<Int>());
    }

    /**
     * Find the most specific "onError" MethodInfo for the specified service.
     */
    MethodInfo? findOnError(Int wsid) {
        WebServiceInfo[] services = this.services;
        String           path     = services[wsid].path;

        // the most specific route has the priority
        for (Int id : wsid..0) {
            WebServiceInfo serviceInto = services[id];
            if (path.startsWith(serviceInto.path), MethodInfo onError ?= serviceInto.onError) {
                return onError;
            }
        }
        return Null;
    }

    /**
     * The WebService info.
     */
    static const WebServiceInfo(Int            id,
                                String         path,
                                WSConstructor  constructor,
                                EndpointInfo[] endpoints,
                                EndpointInfo?  defaultGet,
                                MethodInfo[]   interceptors,
                                MethodInfo[]   observers,
                                MethodInfo?    onError
                                ) {
        /**
         * The number of endpoints for this WebService.
         */
        Int endpointCount.get() {
            return endpoints.size + (defaultGet == Null ? 0 : 1);
        }
    }

    /**
     * The method info for a given WebService id.
     */
    static const MethodInfo(Method<WebService> method, Int wsid) {
        /**
         * The HTTP Method.
         */
        HttpMethod? httpMethod.get() {
            return method.is(Observe|Intercept)
                    ? method.httpMethod
                    : Null;
        }
    }

    /**
     * The endpoint info for a given WebService id.
     */
    static const EndpointInfo
            extends MethodInfo {
        construct(Endpoint method, Int id, Int wsid,
                 String                servicePath,
                 Boolean               serviceTls,
                 Boolean               serviceRedirectTls,
                 Boolean               serviceRequiresSession,
                 TrustLevel            serviceTrust,
                 MediaType|MediaType[] serviceProduces,
                 MediaType|MediaType[] serviceConsumes,
                 Restriction           serviceRestriction,
                 Boolean               serviceStreamRequest,
                 Boolean               serviceStreamResponse
                 ) {
            this.id = id;
            construct MethodInfo(method, wsid);

            String templateString = method.template;
            if (templateString == "" || templateString == "/") {
                this.template = UriTemplate.ROOT;
            } else {
                switch (templateString[0]) {
                case '{':       // matching section/variable binding
                case '/':       // path expected
                case '?':       // query expected
                case '#':       // fragment expected
                    break;
                default:
                    templateString = '/' + templateString;
                    break;
                }
                this.template = new UriTemplate(templateString);
            }

            // check if the template matches UriParam's in the method
            Int     requiredParamCount   = 0;
            Boolean requiredSessionParam = False;
            Boolean hasBodyParam         = False;
            for (Parameter param : method.params) {
                // well-known types are Session and RequestIn (see ChainBundle.ensureCallChain)
                if (param.ParamType == RequestIn      ||
                    param.is(QueryParam)              ||
                    param.defaultValue()) {
                    continue;
                }
                if (param.is(BodyParam)) {
                    if (hasBodyParam) {
                        throw new IllegalState($|The template for method "{method}" has more than \
                                                |one "@BodyParam" annotated parameter
                                              );
                    }
                    hasBodyParam = True;
                    continue;
                }

                if (param.ParamType.is(Type<Session>)) {
                    requiredSessionParam = True;
                    continue;
                }

                assert String name := param.hasName();
                if (param.is(UriParam)) {
                    name ?= param.bindName;
                }
                if (!template.vars.contains(name)) {
                    throw new IllegalState($|The template for method "{method}" is missing \
                                            |a variable name "{name}": "{templateString}"
                                          );
                }
                requiredParamCount++;
            }
            this.requiredParamCount = requiredParamCount;

            this.requiresTls = serviceTls
                        ? !method.is(HttpsOptional)
                        :  method.is(HttpsRequired);

            this.redirectTls = serviceRedirectTls ||
                        method.is(HttpsRequired) && method.autoRedirect;

            if (method.is(SessionOptional)) {
                if (requiredSessionParam) {
                    throw new IllegalState($|Invalid "@SessionOptional" annotation for endpoint \
                                            |"{method}"; parameters require a session
                                          );
                }
                if (method.is(SessionRequired)) {
                    throw new IllegalState($|Contradicting "@SessionRequired" and "@SessionOptional" \
                                            |annotations for endpoint "{method}"
                                          );
                }
                this.requiresSession = False;
            } else {
                this.requiresSession = serviceRequiresSession || method.is(SessionRequired) ||
                                       requiredSessionParam;
            }

            this.requiredTrust = switch (method.is(_)) {
                case LoginRequired: TrustLevel.maxOf(serviceTrust, method.security);
                case LoginOptional: None; // explicitly optional overrides service trust level
                default:            serviceTrust;
            };

            this.produces = method.is(Produces)
                        ? method.produces
                        : serviceProduces;
            this.consumes = method.is(Consumes)
                        ? method.consumes
                        : serviceConsumes;
            this.requiredPermission = method.is(Restrict)
                        ? computeRestriction(method, servicePath == "/" && !templateString.empty
                                ? templateString
                                : servicePath + templateString)
                        : serviceRestriction;

            this.allowRequestStreaming  = method.is(StreamingRequest)  || serviceStreamRequest;
            this.allowResponseStreaming = method.is(StreamingResponse) || serviceStreamResponse;
        }

        @Override
        Endpoint method;

        @Override
        HttpMethod httpMethod.get() {
            return method.httpMethod;
        }

        /**
         * Indicates if the endpoint return value is a _conditional return_.
         */
        Boolean conditionalResult.get() {
            return method.conditionalResult;
        }

        /**
         * The endpoint id.
         */
        Int id;

        /**
         * The URI template.
         */
        UriTemplate template;

        /**
         * The number of required `UriParameters` that are needed to "fulfil" this endpoint.
         */
         Int requiredParamCount;

        /**
         * The media type(s) this endpoint consumes.
         */
        MediaType|MediaType[] consumes;

        /**
         * The media type(s) this endpoint produces.
         */
        MediaType|MediaType[] produces;

        /**
         * Indicates if this endpoint requires a [Session].
         */
        Boolean requiresSession;

        /**
         * Indicates if this endpoint requires HTTPS.
         */
        Boolean requiresTls;

        /**
         * Indicates if an attempt to access the endpoint should automatically redirect to HTTPS.
         */
        Boolean redirectTls;

        /**
         *  [TrustLevel] of security that is required by the this endpoint.
         */
        TrustLevel requiredTrust;

        /**
         * If not `Null` (which means there are no restrictions), contains the permission name or a
         * method to check for permissions.
         */
        Restriction requiredPermission;

        /**
         * Indicates if this endpoint allows the request content not to be fully buffered.
         */
        Boolean allowRequestStreaming;

        /**
         * Indicates if this endpoint allows the response content not to be fully buffered.
         */
        Boolean allowResponseStreaming;

        /**
         * Test if the specified URI matches this endpoint.
         *
         * @param uri  the `Uri` to test to see if it matches this endpoint
         *
         * @return a map from variable name to value
         */
        conditional UriParameters matches(Uri|String uri) {
            if (UriParameters uriParams      := template.matches(uri),
                              uriParams.size >= requiredParamCount) {
                return True, uriParams;
            }
            return False;
        }
    }


    // ----- Catalog building ----------------------------------------------------------------------

    /**
     * Build a Catalog for a WebApp.
     *
     * @param app     the WebApp singleton instance
     * @param extras  (optional) a map of WebService classes for processing requests for
     *                corresponding paths
     */
    static Catalog buildCatalog(WebApp app, Map<Class<WebService>, WSConstructor> extras = []) {
        ClassInfo[] classInfos    = new ClassInfo[];
        Class[]     sessionAnnos = new Class[];

        Set<String> declaredPaths = new HashSet<String>();

        // collect ClassInfos for "extras"; this should be done first to account for services
        // without default constructors, which those extra services possibly are
        if (!extras.empty) {
            for ((Class<WebService> clz, WSConstructor constructor) : extras) {
                if (AnnotationTemplate webServiceAnno := clz.annotatedBy(WebService)) {
                    String path = extractPath(webServiceAnno);
                    path = validatePath(path, declaredPaths, webServiceAnno);
                    classInfos += new ClassInfo(path, clz, constructor);

                    // scan classes inside the extra
                    scanClasses(clz.childClasses, classInfos, sessionAnnos, declaredPaths);
                } else {
                    throw new IllegalState($|"WebService" annotation is missing for "{clz}"
                                          );
                }
            }
        }

        // helper method to retrieve WebService "extras" from an ExtraAware instance
        private void collectExtras(ExtrasAware extraAware, ClassInfo[] classInfos,
                                   Class[] sessionAnnos, Set<String> declaredPaths) {
            for ((Duplicable+WebService) extra : extraAware.extras) {
                Class<WebService> clz  = &extra.actualClass.as(Class<WebService>);
                String            path = extra.path;
                path = validatePath(path, declaredPaths, clz);
                classInfos += new ClassInfo(path, clz, () -> extra.duplicate());

                // scan classes inside the extra
                scanClasses(clz.childClasses, classInfos, sessionAnnos, declaredPaths);
            }
        }

        // collect the Broker extras
        collectExtras(app.sessionBroker.is(ExtrasAware)?, classInfos, sessionAnnos, declaredPaths);

        // collect the Authenticator extras
        collectExtras(app.authenticator.is(ExtrasAware)?, classInfos, sessionAnnos, declaredPaths);

        // collect the ClassInfos for standard WebServices and Session annotations
        scanClasses(app.classes, classInfos, sessionAnnos, declaredPaths);

        // sort the ClassInfos based on their paths (SystemService goes first)
        classInfos.sorted((ci1, ci2) -> (ci1.path <=> ci2.path).reversed, inPlace=True);

        // collect all of the endpoints into a Catalog
        WebServiceInfo[] webServiceInfos = collectEndpoints(app, classInfos);
        return new Catalog(app, webServiceInfos, sessionAnnos);
    }

    /**
     * WebService info collected during the scan phase.
     */
    private static const ClassInfo(String path, Class<WebService> clz, WSConstructor constructor);

    /**
     * Scan all the specified classes for WebServices and add the corresponding information
     * to the ClassInfo array along with session annotations array.
     */
    private static void scanClasses(Class[] classes, ClassInfo[] classInfos, Class[] sessionAnnos,
                             Set<String> declaredPaths) {
        for (Class child : classes) {
            if (child.annotatedBy(Abstract)) {
                continue;
            }

            if (AnnotationTemplate webServiceAnno := child.annotatedBy(WebService)) {
                assert child.is(Class<WebService>);

                Type<WebService> serviceType = child.PublicType;
                if (WSConstructor constructor := serviceType.defaultConstructor()) {
                    String path = extractPath(webServiceAnno);
                    path = validatePath(path, declaredPaths, webServiceAnno);
                    classInfos += new ClassInfo(path, child, constructor);
                } else {
                    // the WebService without a default constructor might have been one of the
                    // "extras"; we should still scan its children classes
                    if (!classInfos.any(info -> info.clz == child)) {
                        throw new IllegalState($|The default constructor is missing for "{child}"
                                               );
                    }
                }

                // scan classes inside the WebService class
                scanClasses(child.childClasses, classInfos, sessionAnnos, declaredPaths);
            } else if (child.baseTemplate.format == Annotation && child.mixesInto(Session)) {
                sessionAnnos += child;
            } else if (child.implements(Package), Object pkg := child.isSingleton()) {
                assert pkg.is(Package);

                // don't scan imported modules
                if (!pkg.isModuleImport()) {
                    scanClasses(pkg.as(Package).classes, classInfos, sessionAnnos, declaredPaths);
                }
            }
        }
    }

    /**
     * Extract a [WebService] path from teh specified template.
     */
    private static String extractPath(AnnotationTemplate webServiceAnno) {
        Argument[] args = webServiceAnno.arguments;
        assert !args.empty;

        String path;
        if (!(path := args[0].value.is(String))) {
            throw new IllegalState($|WebService "{webServiceAnno}": first argument is not a path
                                  );
        }
        return path;
    }

    /**
     * Validate the uniqueness of the [WebService] path and add it to `declaredPaths`.
     */
    private static String validatePath(String                   path,
                                       Set<String>              declaredPaths,
                                       AnnotationTemplate|Class webService,
                                      ) {
        if (path == "" || path == "/") {
            path = "/";
        } else {
            while (path.endsWith('/')) {
                // while the service path represents a "directory", we normalize it, so it
                // does not end with the '/' (except for the root)
                path = path[0 ..< path.size-1];
            }

            if (!path.startsWith('/')) {
                // the service path is always a "root"
                path = "/" + path;
            }
        }

        if (declaredPaths.contains(path)) {
            String name = webService.is(AnnotationTemplate)
                    ? webService.template.displayName
                    : webService.displayName;
            throw new IllegalState($|WebService "{name}": path "{path}" is already in use
                                    );
        }
        declaredPaths += path;
        return path;
    }

    /**
     * Collect all endpoints for the WebServices in the specified ClassInfo array and
     * create a corresponding WebServiceInfo array.
     */
    private static WebServiceInfo[] collectEndpoints(WebApp app, ClassInfo[] classInfos) {
        typedef MediaType|MediaType[] as MediaTypes;

        Class       clzWebApp          = &app.actualClass;
        TrustLevel  appTrustLevel      = clzWebApp.is(LoginRequired) ? clzWebApp.security : None;
        Boolean     appTls             = clzWebApp.is(HttpsRequired);
        Boolean     appRedirectTls     = clzWebApp.is(HttpsRequired) && clzWebApp.autoRedirect;
        Boolean     appRequiresSession = clzWebApp.is(SessionRequired);
        MediaTypes  appProduces        = clzWebApp.is(Produces) ? clzWebApp.produces : [];
        MediaTypes  appConsumes        = clzWebApp.is(Consumes) ? clzWebApp.consumes : [];
        Restriction appRestriction     = clzWebApp.is(Restrict) ? computeRestriction(clzWebApp) : Null;
        Boolean     appStreamRequest   = clzWebApp.is(StreamingRequest);
        Boolean     appStreamResponse  = clzWebApp.is(StreamingResponse);

        Int wsid = 0;
        Int epid = 0;

        WebServiceInfo[] webServiceInfos = new Array(classInfos.size);
        for (ClassInfo classInfo : classInfos) {
            Class<WebService> clz         = classInfo.clz;
            Type<WebService>  serviceType = clz.PublicType;
            String            servicePath = classInfo.path;

            TrustLevel serviceTrust       = appTrustLevel;
            Boolean    serviceTls         = appTls;
            Boolean    serviceRedirectTls = appRedirectTls;
            if (clz.is(LoginRequired)) {
                if (clz.is(LoginOptional)) {
                    throw new IllegalState($|Contradicting "@LoginRequired" and "@LoginOptional" \
                                            |annotations for service "{clz}"
                                          );
                }
                serviceTrust       = clz.security;
                serviceTls         = True;
                serviceRedirectTls = clz.autoRedirect;
            } else {
                if (clz.is(LoginOptional)) {
                    serviceTrust = None;
                }
                if (clz.is(HttpsOptional)) {
                    if (clz.is(HttpsRequired)) {
                        throw new IllegalState($|Contradicting "@HttpsRequired" and "@HttpsOptional" \
                                                |annotations for service "{clz}"
                                              );
                    }
                    serviceTls = False;
                } else if (clz.is(HttpsRequired)) {
                    serviceTls         = True;
                    serviceRedirectTls = clz.autoRedirect;
                }
            }
            Boolean serviceRequiresSession = appRequiresSession;
            if (clz.is(SessionRequired)) {
                if (clz.is(SessionOptional)) {
                    throw new IllegalState($|Contradicting "@SessionRequired" and "@SessionOptional" \
                                            |annotations for service "{clz}"
                                          );
                }
                serviceRequiresSession = True;
            } else {
                serviceRequiresSession &= !clz.is(SessionOptional);
            }

            MediaTypes  serviceProduces       = clz.is(Produces) ? clz.produces : appProduces;
            MediaTypes  serviceConsumes       = clz.is(Consumes) ? clz.consumes : appConsumes;
            Restriction serviceRestriction    = clz.is(Restrict) ? computeRestriction(clz) : appRestriction;
            Boolean     serviceStreamRequest  = clz.is(StreamingRequest)  || appStreamRequest;
            Boolean     serviceStreamResponse = clz.is(StreamingResponse) || appStreamResponse;

            EndpointInfo[] endpoints    = new EndpointInfo[];
            EndpointInfo?  defaultGet   = Null;
            MethodInfo[]   interceptors = new MethodInfo[];
            MethodInfo[]   observers    = new MethodInfo[];
            MethodInfo?    onError      = Null;

            static void validateEndpoint(Method method) {
                Int returnCount = method.returns.size;
                assert returnCount <= 1 ||
                       returnCount == 2 && method.conditionalResult
                            as $"endpoint \"{method}\" has multiple returns";
            }

            Set<String> templates = new HashSet();
            for (Method<WebService, Tuple, Tuple> method : serviceType.methods) {
                switch (method.is(_)) {
                case Default:
                    if (defaultGet == Null) {
                        if (method.httpMethod != GET) {
                            throw new IllegalState($|WebService "{clz}": "Default" is only \
                                                    |applicable to "@Get" endpoints
                                                    );
                        }
                        if (method.template != "") {
                            throw new IllegalState($|WebService "{clz}": non-empty uri template \
                                                    |for "Default" endpoint "{method}"
                                                    );
                        }
                        validateEndpoint(method);
                        defaultGet = new EndpointInfo(method, epid++, wsid, servicePath,
                                        serviceTls, serviceRedirectTls,
                                        serviceRequiresSession, serviceTrust,
                                        serviceProduces, serviceConsumes, serviceRestriction,
                                        serviceStreamRequest, serviceStreamResponse);
                    } else {
                        throw new IllegalState($|Multiple "Default" endpoints on "{clz}"
                                                );
                    }
                    break;

                case Endpoint:
                    validateEndpoint(method);

                    EndpointInfo info = new EndpointInfo(method, epid++, wsid, servicePath,
                                        serviceTls, serviceRedirectTls,
                                        serviceRequiresSession, serviceTrust,
                                        serviceProduces, serviceConsumes, serviceRestriction,
                                        serviceStreamRequest, serviceStreamResponse);
                    if (templates.addIfAbsent($"{info.httpMethod.name} {info.template}")) {
                        endpoints.add(info);
                    } else {
                        throw new IllegalState($|WebService "{clz}": a duplicate use of template \
                                                |"{info.template}" by the endpoint "{method}"
                                                );
                    }
                    break;

                case Intercept, Observe:
                    interceptors.add(new MethodInfo(method, wsid));
                    break;

                case OnError:
                    if (onError == Null) {
                        onError = new MethodInfo(method, wsid);
                    } else {
                        throw new IllegalState($|Multiple "OnError" handlers on "{clz}"
                                              );
                    }
                    break;
                }
            }

            // sort the endpoints based on their UriTemplates in such a way, that:
            //    1) otherwise, the one with a longer prefix goes first
            //    2) the one with more parts goes first
            function Ordered(EndpointInfo, EndpointInfo) order = (ep1, ep2) -> {
                        UriTemplate t1  = ep1.template;
                        UriTemplate t2  = ep2.template;
                        Ordered     cmp = t2.literalPrefix <=> t1.literalPrefix;
                        return cmp == Equal
                            ? t2.parts.size <=> t1.parts.size
                            : cmp;
                    };

            // we never use the endpoint id as an index, so we can sort them in-place
            endpoints.sorted(order, inPlace=True);

            endpoints   .freeze(inPlace=True);
            interceptors.freeze(inPlace=True);
            observers   .freeze(inPlace=True);

            webServiceInfos += new WebServiceInfo(wsid++,
                    servicePath, classInfo.constructor,
                    endpoints, defaultGet, interceptors, observers, onError);
        }
        return webServiceInfos;
    }

    static Restriction computeRestriction(Restrict restrict, String? templateString = Null) {
        String?|Method<WebService, <>, <Boolean>> permission = restrict.permission;

        if (permission.is(String?)) {
            if (!restrict.is(Endpoint)) {
                assert permission != Null as $"The @Restrict annotation must specify the permission";
                return new Permission(permission);
            }

            String      restrictTarget   = templateString ?: restrict.template;
            UriTemplate restrictTemplate = new UriTemplate(restrictTarget);
            String[]    restrictVars     = restrictTemplate.vars;
            if (permission == Null) {
                // the permission is not specified by the Restrict; compute the default value
                if (restrictVars.empty) {
                    // e.g. "GET:/api/accounts"
                    return new Permission(restrictTarget, restrict.httpMethod.name);
                } else {
                    // e.g. "GET:/api/accounts{/id}"
                    return (request) -> new Permission(request.template.format(request.matchResult),
                                                       request.method.name);
                }
            }

            // permission target can be parameterized; we need to resolve it at run time
            Permission  rawPermission      = new Permission(permission);
            String      permissionTarget   = rawPermission.target;
            UriTemplate permissionTemplate = new UriTemplate(permissionTarget);
            String[]    permissionVars     = permissionTemplate.vars;
            if (permissionVars.empty) {
                return rawPermission;
            }
            assert restrictVars.containsAll(permissionVars) as
                    $|Restrict permission "{permission}" contains elements that are missing in \
                     |the endpoint template "{restrict.template}"
                     ;
            String action = rawPermission.action;
            return (request) ->
                new Permission(permissionTemplate.format(request.matchResult), action);
        }
        return permission; // Method<WebService, <>, <Boolean>>
    }
}