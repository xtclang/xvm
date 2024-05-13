import ecstasy.reflect.AnnotationTemplate;
import ecstasy.reflect.Argument;

import net.UriTemplate;
import net.UriTemplate.UriParameters;

import web.*;

import WebService.Constructor;

/**
 * The catalog of WebApp endpoints.
 */
const Catalog(WebApp webApp, String systemPath, WebServiceInfo[] services, Class[] sessionMixins) {
    /**
     * The list of [WebServiceInfo] objects describing the [WebService] classes discovered within
     * the application.
     */
    WebServiceInfo[] services;

    /**
     * The list of [Session] mixin classes discovered within the application.
     */
    Class[] sessionMixins;

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
                                Constructor    constructor,
                                EndpointInfo[] endpoints,
                                EndpointInfo?  defaultEndpoint,
                                MethodInfo[]   interceptors,
                                MethodInfo[]   observers,
                                MethodInfo?    onError,
                                MethodInfo?    route
                                ) {
        /**
         * The number of endpoints for this WebService.
         */
        Int endpointCount.get() {
            return endpoints.size + (defaultEndpoint == Null ? 0 : 1);
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
                 Boolean               serviceTls,
                 TrustLevel            serviceTrust,
                 MediaType|MediaType[] serviceProduces,
                 MediaType|MediaType[] serviceConsumes,
                 String|String[]       serviceSubjects,
                 Boolean               serviceStreamRequest,
                 Boolean               serviceStreamResponse
                 ) {
            this.id = id;
            construct MethodInfo(method, wsid);

            String templateString = method.template;
            while (templateString.startsWith('/')) {
                // the endpoint path is always relative
                templateString = templateString.substring(1);
            }

            this.template = templateString == "" || templateString == "/"
                ? UriTemplate.ROOT
                : new UriTemplate(templateString);

            // check if the template matches UriParam's in the method
            Int requiredParamCount = 0;
            for (Parameter param : method.params) {
                // well-known types are Session and RequestIn (see ChainBundle.ensureCallChain)
                if (param.ParamType.is(Type<Session>) ||
                    param.ParamType == RequestIn      ||
                    param.is(QueryParam)              ||
                    param.is(BodyParam)               ||
                    param.defaultValue()) {
                    continue;
                }

                assert String name := param.hasName();
                if (param.is(UriParam)) {
                    name ?= param.bindName;
                }
                if (!template.vars.contains(name)) {
                    throw new IllegalState($|The template for method "{method}" is missing \
                                            |a variable name "{name}": \
                                            |"{templateString}"
                                        );
                }
                requiredParamCount++;
            }
            this.requiredParamCount = requiredParamCount;

            this.requiresTls = serviceTls
                        ? !method.is(HttpsOptional)
                        :  method.is(HttpsRequired);

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

            String|String[] subjects = method.is(Restrict)
                        ? method.subject
                        : serviceSubjects;

            this.restrictSubjects       = subjects.is(String) ? [subjects] : subjects;
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
         * Indicates if this endpoint requires the HTTPS.
         */
        Boolean requiresTls;

        /**
         *  [TrustLevel] of security that is required by the this endpoint.
         */
        TrustLevel requiredTrust;

        /**
         * If not empty, contains users that this endpoint is restricted to.
         */
        String[] restrictSubjects;

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

        /**
         * Check if any of the specified roles matches the restrictions of this endpoint.
         *
         * @param roles  the set of roles to check against
         *
         * @return True iff any of the roles matches this endpoint restrictions
         */
        Boolean authorized(Set<String> roles) {
            if (restrictSubjects.empty) {
                return True;
            }

            for (String role : restrictSubjects) {
                if (roles.contains(role)) {
                    return True;
                }
            }
            return False;
        }
    }


    // ----- Catalog building ----------------------------------------------------------------------

    /**
     * The default path prefix for the system service. The actual `systemPath` property is computed
     * by [buildCatalog()] method to ensure its uniqueness.
     */
    static String DefaultSystemPath = "/xverify";

    /**
     * Build a Catalog for a WebApp.
     *
     * @param app     the WebApp singleton instance
     * @param extras  (optional) a map of WebService classes for processing requests for
     *                corresponding paths
     */
    static Catalog buildCatalog(WebApp app, Map<Class<WebService>, Constructor> extras = []) {
        ClassInfo[] classInfos    = new ClassInfo[];
        Class[]     sessionMixins = new Class[];

        Set<String> declaredPaths = new HashSet<String>();

        // collect ClassInfos for "extras"; this should be done first to account for services
        // without default constructors, which those extra services possibly are
        if (!extras.empty) {
            for ((Class<WebService> clz, Constructor constructor) : extras) {
                if (AnnotationTemplate webServiceAnno := clz.annotatedBy(WebService)) {
                    String path = extractPath(webServiceAnno, declaredPaths);
                    classInfos += new ClassInfo(path, clz, constructor);
                } else {
                    throw new IllegalState($|"WebService" annotation is missing for "{clz}"
                                          );
                }
            }
        }

        // collect the ClassInfos for standard WebServices and Session mixins
        scanClasses(app.classes, classInfos, sessionMixins, declaredPaths);

        // compute the system service name and add the system service info
        String systemPath = DefaultSystemPath;
        for (Int i = 0; declaredPaths.contains(systemPath); i++) {
            systemPath = $"{DefaultSystemPath}_{i}";
        }
        classInfos += new ClassInfo(systemPath, SystemService,
                        SystemService.PublicType.defaultConstructor() ?: assert);

        // sort the ClassInfos based on their paths (SystemService goes first)
        classInfos.sorted((ci1, ci2) ->
            ci1.path == systemPath ? Lesser : (ci1.path <=> ci2.path).reversed, inPlace=True);

        // now collect all endpoints
        WebServiceInfo[] webServiceInfos = collectEndpoints(app, classInfos);
        assert webServiceInfos[0].path == systemPath;

        return new Catalog(app, systemPath, webServiceInfos, sessionMixins);
    }

    /**
     * WebService info collected during the scan phase.
     */
    private static const ClassInfo(String path, Class<WebService> clz, Constructor constructor);

    /**
     * Scan all the specified classes for WebServices and add the corresponding information
     * to the ClassInfo array along with session mixin class array.
     */
    private static void scanClasses(Class[] classes, ClassInfo[] classInfos, Class[] sessionMixins,
                             Set<String> declaredPaths) {
        for (Class child : classes) {
            if (child.annotatedBy(Abstract)) {
                continue;
            }

            if (AnnotationTemplate webServiceAnno := child.annotatedBy(WebService)) {
                 // TODO GG: drop protected and private
                assert child.is(Class<WebService, (protected WebService), (private WebService)>);

                Type<WebService> serviceType = child.PublicType;
                if (Constructor constructor := serviceType.defaultConstructor()) {
                    String path = extractPath(webServiceAnno, declaredPaths);

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
                Collection<Type>  childTypes   = child.PrivateType.childTypes.values;
                @Volatile Class[] childClasses = new Class[];
                childTypes.forEach(t -> {
                    if (Class c := t.fromClass()) {
                        childClasses += c;
                    }
                });

                scanClasses(childClasses, classInfos, sessionMixins, declaredPaths);
            } else if (child.mixesInto(Session)) {
                sessionMixins += child;
            } else if (child.implements(Package), Object pkg := child.isSingleton()) {
                assert pkg.is(Package);

                // don't scan imported modules
                if (!pkg.isModuleImport()) {
                    scanClasses(pkg.as(Package).classes, classInfos, sessionMixins, declaredPaths);
                }
            }
        }
    }

    /**
     * Extract and validate the uniqueness of the [WebService] path.
     */
    private static String extractPath(AnnotationTemplate webServiceAnno, Set<String> declaredPaths) {
        Argument[] args = webServiceAnno.arguments;
        assert !args.empty;

        String path;
        if (!(path := args[0].value.is(String))) {
            throw new IllegalState($|WebService "{webServiceAnno}": first argument is not a path
                                  );
        }

        if (path != "/") {
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
            throw new IllegalState($|WebService "{webServiceAnno}": path "{path}" is already in use
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
        typedef String|String[]       as Subjects;

        Class      clzWebApp         = &app.actualClass;
        TrustLevel appTrustLevel     = clzWebApp.is(LoginRequired) ? clzWebApp.security : None;
        Boolean    appTls            = clzWebApp.is(HttpsRequired);
        MediaTypes appProduces       = clzWebApp.is(Produces) ? clzWebApp.produces : [];
        MediaTypes appConsumes       = clzWebApp.is(Consumes) ? clzWebApp.consumes : [];
        Subjects   appSubjects       = clzWebApp.is(Restrict) ? clzWebApp.subject  : [];
        Boolean    appStreamRequest  = clzWebApp.is(StreamingRequest);
        Boolean    appStreamResponse = clzWebApp.is(StreamingResponse);

        Int wsid = 0;
        Int epid = 0;

        WebServiceInfo[] webServiceInfos = new Array(classInfos.size);
        for (ClassInfo classInfo : classInfos) {
            Class<WebService> clz         = classInfo.clz;
            Type<WebService>  serviceType = clz.PublicType;

            TrustLevel serviceTrust = appTrustLevel;
            Boolean    serviceTls   = appTls;
            if (clz.is(LoginRequired)) {
                serviceTrust = clz.security;
                serviceTls   = True;
            } else {
                if (clz.is(LoginOptional)) {
                    serviceTrust = None;
                }
                if (clz.is(HttpsOptional)) {
                    serviceTls = False;
                } else if (clz.is(HttpsRequired)) {
                    serviceTls = True;
                }
            }
            MediaTypes serviceProduces       = clz.is(Produces) ? clz.produces : appProduces;
            MediaTypes serviceConsumes       = clz.is(Consumes) ? clz.consumes : appConsumes;
            Subjects   serviceSubjects       = clz.is(Restrict) ? clz.subject  : appSubjects;
            Boolean    serviceStreamRequest  = clz.is(StreamingRequest) || appStreamRequest;
            Boolean    serviceStreamResponse = clz.is(StreamingResponse) || appStreamResponse;

            EndpointInfo[] endpoints       = new EndpointInfo[];
            EndpointInfo?  defaultEndpoint = Null;
            MethodInfo[]   interceptors    = new MethodInfo[];
            MethodInfo[]   observers       = new MethodInfo[];
            MethodInfo?    onError         = Null;
            MethodInfo?    route           = Null;

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
                    if (defaultEndpoint == Null) {
                        if (method.template != "") {
                            throw new IllegalState($|WebService "{clz}": non-empty uri template \
                                                    |for "Default" endpoint "{method}"
                                                    );
                        }
                        validateEndpoint(method);
                        defaultEndpoint = new EndpointInfo(method, epid++, wsid,
                                            serviceTls, serviceTrust,
                                            serviceProduces, serviceConsumes, serviceSubjects,
                                            serviceStreamRequest, serviceStreamResponse);
                    } else {
                        throw new IllegalState($|multiple "Default" endpoints on "{clz}"
                                                );
                    }
                    break;

                case Endpoint:
                    validateEndpoint(method);

                    EndpointInfo info = new EndpointInfo(method, epid++, wsid,
                                        serviceTls, serviceTrust,
                                        serviceProduces, serviceConsumes, serviceSubjects,
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
                        throw new IllegalState($|multiple "OnError" handlers on "{clz}"
                                              );
                    }
                    break;

                default:
                    if (method.name == "route" && method.params.size >= 4 &&
                            method.params[0].ParamType == Session         &&
                            method.params[1].ParamType == RequestIn       &&
                            method.params[2].ParamType == Handler         &&
                            method.params[3].ParamType == ErrorHandler) {
                        assert route == Null;
                        route = new MethodInfo(method, wsid);
                    }
                    break;
                }
            }

            // sort the endpoints based on their UriTemplates in such a way, that:
            //    1) the one with more parts goes first
            //    2) otherwise, the one with a longer prefix goes first
            function Ordered (EndpointInfo, EndpointInfo) order = (ep1, ep2) -> {
                        UriTemplate t1  = ep1.template;
                        UriTemplate t2  = ep2.template;
                        Ordered     cmp = t2.parts.size <=> t1.parts.size;
                        return cmp == Equal
                            ? t2.literalPrefix <=> t1.literalPrefix
                            : cmp;
                    };

            // we never use the endpoint id as an index, so we can sort them in-place
            endpoints.sorted(order, inPlace=True);

            endpoints   .freeze(inPlace=True);
            interceptors.freeze(inPlace=True);
            observers   .freeze(inPlace=True);

            webServiceInfos += new WebServiceInfo(wsid++,
                    classInfo.path, classInfo.constructor,
                    endpoints, defaultEndpoint,
                    interceptors, observers, onError, route
                    );
        }
        return webServiceInfos;
    }
}