import ecstasy.reflect.AnnotationTemplate;
import ecstasy.reflect.Argument;

import web.Consumes;
import web.Default;
import web.Endpoint;
import web.ErrorHandler;
import web.HttpMethod;
import web.HttpsOptional;
import web.HttpsRequired;
import web.Intercept;
import web.LoginOptional;
import web.LoginRequired;
import web.MediaType;
import web.Observe;
import web.OnError;
import web.Produces;
import web.Restrict;
import web.StreamingRequest;
import web.StreamingResponse;
import web.TrustLevel;
import web.WebService;

import net.UriTemplate;


/**
 * The catalog of WebApp endpoints.
 */
const Catalog(WebApp webApp, String systemPath, WebServiceInfo[] services, Class[] sessionMixins)
    {
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
    Int serviceCount.get()
        {
        return services.size;
        }

    /**
     * The total number of endpoints.
     */
    @Lazy Int endpointCount.calc()
        {
        return services.map(WebServiceInfo.endpointCount).reduce(new aggregate.Sum<Int>());
        }

    /**
     * Find the most specific "onError" MethodInfo for the specified service.
     */
    MethodInfo? findOnError(Int wsid)
        {
        WebServiceInfo[] services = this.services;
        String           path     = services[wsid].path;

        // the most specific route has the priority
        for (Int id : wsid..0)
            {
            WebServiceInfo serviceInto = services[id];
            if (path.startsWith(serviceInto.path), MethodInfo onError ?= serviceInto.onError)
                {
                return onError;
                }
            }
        return Null;
        }

    /**
     * The function that represents a default WebService constructor.
     */
    typedef function WebService() as ServiceConstructor;

    /**
     * The WebService info.
     */
    static const WebServiceInfo<ServiceType extends WebService>(
                                Int                id,
                                String             path,
                                ServiceConstructor constructor,
                                EndpointInfo[]     endpoints,
                                EndpointInfo?      defaultEndpoint,
                                MethodInfo[]       interceptors,
                                MethodInfo[]       observers,
                                MethodInfo?        onError,
                                MethodInfo?        route
                                )
        {
        /**
         * The number of endpoints for this WebService.
         */
        Int endpointCount.get()
            {
            return endpoints.size + (defaultEndpoint == Null ? 0 : 1);
            }
        }

    /**
     * The method info for a given WebService id.
     */
    static const MethodInfo(Method<WebService> method, Int wsid)
        {
        /**
         * The HTTP Method.
         */
        HttpMethod? httpMethod.get()
            {
            return method.is(Observe|Intercept)
                    ? method.httpMethod
                    : Null;
            }
        }

    /**
     * The endpoint info for a given WebService id.
     */
    static const EndpointInfo
            extends MethodInfo
        {
        construct(Endpoint method, Int id, Int wsid,
                 Boolean               serviceTls,
                 TrustLevel            serviceTrust,
                 MediaType|MediaType[] serviceProduces,
                 MediaType|MediaType[] serviceConsumes,
                 String|String[]       serviceSubjects,
                 Boolean               serviceStreamRequest,
                 Boolean               serviceStreamResponse
                 )
            {
            this.id = id;
            construct MethodInfo(method, wsid);

            String template = method.template;
            while (template.startsWith('/'))
                {
                // the endpoint path is always relative
                template = template.substring(1);
                }

            this.template = template == "" || template == "/"
                ? UriTemplate.ROOT
                : new UriTemplate(template);

            this.requiresTls = serviceTls
                        ? !method.is(HttpsOptional)
                        :  method.is(HttpsRequired);

            this.requiredTrust = switch (method.is(_))
                {
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

            this.restrictSubjects = method.is(Restrict)
                        ? method.subject
                        : serviceSubjects;

            this.allowRequestStreaming  = method.is(StreamingRequest)  || serviceStreamRequest;
            this.allowResponseStreaming = method.is(StreamingResponse) || serviceStreamResponse;
            }

        @Override
        Endpoint method;

        @Override
        HttpMethod httpMethod.get()
            {
            return method.httpMethod;
            }

        /**
         * Indicates if the endpoint return value is a _conditional return_.
         */
        Boolean conditionalResult.get()
            {
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
        String|String[] restrictSubjects;

        /**
         * Indicates if this endpoint allows the request content not to be fully buffered.
         */
        Boolean allowRequestStreaming;

        /**
         * Indicates if this endpoint allows the response content not to be fully buffered.
         */
        Boolean allowResponseStreaming;
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
     * @param app  the WebApp singleton instance
     */
    static Catalog buildCatalog(WebApp app)
        {
        ClassInfo[] classInfos    = new ClassInfo[];
        Class[]     sessionMixins = new Class[];

        // collect the ClassInfos for WebServices and Session mixins
        Set<String> declaredPaths = new HashSet<String>();
        scanClasses(app.classes, classInfos, sessionMixins, declaredPaths);

        // compute the system service name and add the system service info
        String systemPath = DefaultSystemPath;
        for (Int i = 0; declaredPaths.contains(systemPath); i++)
            {
            systemPath = $"{DefaultSystemPath}_{i}";
            }
        classInfos += new ClassInfo(SystemService, systemPath);

        // sort the ClassInfos based on their paths
        classInfos.sorted((ci1, ci2) ->
            ci1.path == systemPath ? Lesser : (ci1.path <=> ci2.path).reversed, inPlace=True);

        // now collect all endpoints
        WebServiceInfo[] webServiceInfos = collectEndpoints(app, classInfos);
        assert webServiceInfos[0].path == systemPath;

        return new Catalog(app, systemPath, webServiceInfos, sessionMixins);
        }

    /**
     * WebService class/path info collected during the scan phase.
     */
    private static const ClassInfo(Class<WebService> clz, String path);

    /**
     * Scan all the specified classes for WebServices and add the corresponding information
     * to the ClassInfo array along with session mixin class array.
     */
    private static void scanClasses(Class[] classes, ClassInfo[] classInfos, Class[] sessionMixins,
                             Set<String> declaredPaths)
        {
        for (Class child : classes)
            {
            if (AnnotationTemplate webServiceAnno := child.annotatedBy(WebService))
                {
                Argument[] args = webServiceAnno.arguments;
                assert !args.empty;

                String path;
                if (!(path := args[0].value.is(String)))
                    {
                    throw new IllegalState($|WebService "{child}": first argument is not a path
                                          );
                    }

                if (path != "/")
                    {
                    while (path.endsWith('/'))
                        {
                        // while the service path represents a "directory", we normalize it, so it
                        // does not end with the '/' (except for the root)
                        path = path[0 ..< path.size-1];
                        }

                    if (!path.startsWith('/'))
                        {
                        // the service path is always a "root"
                        path = "/" + path;
                        }
                    }

                if (declaredPaths.contains(path))
                    {
                    throw new IllegalState($|WebService "{child}": \
                                            |path {path.quoted()} is already in use
                                            );
                    }

                declaredPaths += path;
                classInfos    += new ClassInfo(child.as(Class<WebService>), path);

                // scan classes inside the WebService class
                Collection<Type> childTypes   = child.PrivateType.childTypes.values;
                Class[]          childClasses = new Class[];
                childTypes.forEach(t ->
                    {
                    if (Class c := t.fromClass())
                        {
                        childClasses += c;
                        }
                    });

                scanClasses(childClasses, classInfos, sessionMixins, declaredPaths);
                }
            else if (child.mixesInto(Session))
                {
                sessionMixins += child;
                }
            else if (child.implements(Package), Object pkg := child.isSingleton())
                {
                assert pkg.is(Package);

                // don't scan imported modules
                if (!pkg.isModuleImport())
                    {
                    scanClasses(pkg.as(Package).classes, classInfos, sessionMixins, declaredPaths);
                    }
                }
            }
        }

    /**
     * Collect all endpoints for the WebServices in the specified ClassInfo array and
     * create a corresponding WebServiceInfo array.
     */
    private static WebServiceInfo[] collectEndpoints(WebApp app, ClassInfo[] classInfos)
        {
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
        for (ClassInfo classInfo : classInfos)
            {
            Class<WebService>  clz         = classInfo.clz;
            Type<WebService>   serviceType = clz.PublicType;
            ServiceConstructor constructor;
            if (!(constructor := serviceType.defaultConstructor()))
                {
                throw new IllegalState($|default constructor is missing for "{clz}"
                                      );
                }

            TrustLevel serviceTrust = appTrustLevel;
            Boolean    serviceTls   = appTls;
            if (clz.is(LoginRequired))
                {
                serviceTrust = clz.security;
                serviceTls   = True;
                }
            else
                {
                if (clz.is(LoginOptional))
                    {
                    serviceTrust = None;
                    }
                if (clz.is(HttpsOptional))
                    {
                    serviceTls = False;
                    }
                else if (clz.is(HttpsRequired))
                    {
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

            static void validateEndpoint(Method method)
                {
                Int returnCount = method.returns.size;
                assert returnCount <= 1 ||
                       returnCount == 2 && method.conditionalResult
                            as $"endpoint \"{method}\" has multiple returns";
                }

            Set<String> templates = new HashSet();
            for (Method<WebService, Tuple, Tuple> method : serviceType.methods)
                {
                switch (method.is(_))
                    {
                    case Default:
                        if (defaultEndpoint == Null)
                            {
                            if (method.template != "")
                                {
                                throw new IllegalState($|WebService "{clz}": non-empty uri template \
                                                        |for "Default" endpoint "{method}"
                                                        );
                                }
                            validateEndpoint(method);
                            defaultEndpoint = new EndpointInfo(method, epid++, wsid,
                                                serviceTls, serviceTrust,
                                                serviceProduces, serviceConsumes, serviceSubjects,
                                                serviceStreamRequest, serviceStreamResponse);
                            }
                        else
                            {
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
                        if (templates.addIfAbsent(info.template.toString()))
                            {
                            endpoints.add(info);
                            }
                        else
                            {
                            throw new IllegalState($|WebService "{clz}": a duplicate use of template \
                                                    |"{info.template}" by the endpoint "{method}"
                                                    );
                            }
                        break;

                    case Intercept, Observe:
                        interceptors.add(new MethodInfo(method, wsid));
                        break;

                    case OnError:
                        if (onError == Null)
                            {
                            onError = new MethodInfo(method, wsid);
                            }
                        else
                            {
                            throw new IllegalState($|multiple "OnError" handlers on "{clz}"
                                                  );
                            }
                        break;

                    default:
                        if (method.name == "route" && method.params.size >= 4 &&
                                method.params[0].ParamType == Session         &&
                                method.params[1].ParamType == RequestIn       &&
                                method.params[2].ParamType == Handler         &&
                                method.params[3].ParamType == ErrorHandler)
                            {
                            assert route == Null;
                            route = new MethodInfo(method, wsid);
                            }
                        break;
                    }
                }

            // we never use the endpoint id as an index, so we can sort them in-place
            endpoints.sorted((ep1, ep2) ->
                    ep2.template.literalPrefix <=> ep1.template.literalPrefix, inPlace=True);

            webServiceInfos += new WebServiceInfo(wsid++,
                    classInfo.path, constructor,
                    endpoints, defaultEndpoint,
                    interceptors, observers, onError, route
                    );
            }
        return webServiceInfos;
        }
    }