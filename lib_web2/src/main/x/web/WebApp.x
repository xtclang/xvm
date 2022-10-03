import ecstasy.reflect.AnnotationTemplate;
import ecstasy.reflect.Argument;
import ecstasy.reflect.ClassTemplate.Composition;

import codecs.Registry;

import routing.Catalog;
import routing.Catalog.EndpointInfo;
import routing.Catalog.MethodInfo;
import routing.Catalog.ServiceConstructor;
import routing.Catalog.WebServiceInfo;
import routing.UriTemplate;


/**
 * The `@WebApp` annotation is used to mark a module as being a web-application module. It can
 * contain any number of discoverable HTTP endpoints.
 *
 * TODO how to import a web module explicitly as "it's ok to trust any web services in this module"
 *      - can the package be annotated as "@Trusted" or something like that?
 */
mixin WebApp
        into Module
    {
    /**
     * Collect all endpoints declared by this Module and assemble them into a Catalog.
     */
    @Lazy Catalog catalog_.calc()
        {
        // REVIEW CP: how to report verification errors

        WebApp      webApp        = this;
        ClassInfo[] classInfos    = new ClassInfo[];
        Class[]     sessionMixins = new Class[];

        // collect the ClassInfos for WebServices
        scanClasses(webApp.classes, classInfos, sessionMixins);

        // sort the ClassInfos based on their paths
        classInfos.sorted((ci1, ci2) -> ci2.path <=> ci1.path, inPlace=True);

        TrustLevel trustLevel  = webApp.is(LoginRequired) ? webApp.security : None;
        Boolean    tlsRequired = webApp.is(HttpsRequired);

        // now collect all endpoints
        WebServiceInfo[] webServiceInfos = collectEndpoints(classInfos, trustLevel, tlsRequired);

        return new Catalog(this, webServiceInfos, sessionMixins);
        }

    /**
     * The registry for this WebApp.
     */
    @Lazy Registry registry_.calc()
        {
        return new Registry();
        }

    /**
     * WebService class/path info collected during the scan phase.
     */
    private static const ClassInfo(Class clz, String path);

    /**
     * Scan all the specified classes for WebServices and add the corresponding information
     * to the ClassInfo array along with session mixin class array.
     */
    private void scanClasses(Class[] classes, ClassInfo[] classInfos, Class[] sessionMixins)
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
                    throw new IllegalState($"WebService's first argument is not a path for \"{child}\"");
                    }

                if (!path.endsWith('/'))
                    {
                    // the service path is always a "directory"
                    path += '/';
                    }

                if (!path.startsWith('/'))
                    {
                    // the service path is always a "root"
                    path = $"/{path}";
                    }
                classInfos += new ClassInfo(child, path);

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

                scanClasses(childClasses, classInfos, sessionMixins);
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
                    scanClasses(pkg.as(Package).classes, classInfos, sessionMixins);
                    }
                }
            }
        }

    /**
     * Collect all endpoints for the WebServices in the specified ClassInfo array and
     * create a corresponding WebServiceInfo array.
     */
    private WebServiceInfo[] collectEndpoints(ClassInfo[] classInfos,
                                              TrustLevel parentTrustLevel,
                                              Boolean    parentTls)
        {
        WebServiceInfo[] webServiceInfos = new Array(classInfos.size);

        Int wsid = 0;
        Int epid = 0;

        for (ClassInfo classInfo : classInfos)
            {
            Class              clz         = classInfo.clz;
            Type<WebService>   serviceType = clz.PublicType.as(Type<WebService>);
            ServiceConstructor constructor;
            if (!(constructor := serviceType.defaultConstructor()))
                {
                throw new IllegalState($"default constructor is missing for \"{clz}\"");
                }

            TrustLevel serviceTrust = parentTrustLevel;
            Boolean    serviceTls   = parentTls;

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

            EndpointInfo[] endpoints       = new EndpointInfo[];
            EndpointInfo?  defaultEndpoint = Null;
            MethodInfo[]   interceptors    = new MethodInfo[];
            MethodInfo[]   observers       = new MethodInfo[];
            MethodInfo?    onError         = Null;
            MethodInfo?    route           = Null;

            for (Method<WebService, Tuple, Tuple> method : serviceType.methods)
                {
                switch (method.is(_))
                    {
                    case Default:
                        if (defaultEndpoint == Null)
                            {
                            String uriTemplate = method.template;
                            if (uriTemplate != "")
                                {
                                throw new IllegalState($|non-empty uri template for \"Default\"\
                                                        |endpoint \"{clz}\"
                                                        );
                                }
                            defaultEndpoint = new EndpointInfo(method, epid++, wsid,
                                                serviceTls, serviceTrust);
                            }
                        else
                            {
                            throw new IllegalState($"multiple \"Default\" endpoints on \"{clz}\"");
                            }
                        break;

                    case Endpoint:
                        endpoints.add(new EndpointInfo(method, epid++, wsid,
                                        serviceTls, serviceTrust));
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
                            throw new IllegalState($"multiple \"OnError\" handlers on \"{clz}\"");
                            }
                        break;

                    default:
                        if (method.name == "route" && method.params.size >= 4 &&
                                method.params[0].ParamType == Session         &&
                                method.params[1].ParamType == Request         &&
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

    /**
     * Handle an otherwise-unhandled exception or other error that occurred during [Request]
     * processing within this `WebApp`, and produce a [Response] that is appropriate to the
     * exception or other error that was raised.
     *
     * @param session   the session (usually non-`Null`) within which the request is being
     *                  processed; the session can be `Null` if the error occurred before or during
     *                  the instantiation of the session
     * @param request   the request being processed
     * @param error     the exception thrown, the error description, or an HttpStatus code
     *
     * @return the [Response] to send back to the caller
     */
    Response handleUnhandledError(Session? session, Request request, Exception|String|HttpStatus error)
        {
        // TODO CP: does the exception need to be logged?
        HttpStatus status = error.is(RequestAborted) ? error.status :
                            error.is(HttpStatus)     ? error
                                                     : InternalServerError;

        return new responses.SimpleResponse(status=status, bytes=error.toString().utf8());
        }
    }