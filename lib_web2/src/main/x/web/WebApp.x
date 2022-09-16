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

        WebServiceInfo[] webServices   = new WebServiceInfo[];
        Class[]          sessionMixins = new Class[];

        scanClasses(this.classes, 0, 0, webServices, sessionMixins);

        return new Catalog(this, webServices, sessionMixins);
        }

    private static (Int, Int) scanClasses(Class[] classes, Int wsid, Int epid,
                                          WebServiceInfo[] webServices, Class[] sessionMixins)
        {
        import ecstasy.reflect.AnnotationTemplate;
        import ecstasy.reflect.Argument;
        import ecstasy.reflect.ClassTemplate.Composition;

        for (Class child : classes)
            {
            if (AnnotationTemplate template := child.annotatedBy(WebService))
                {
                Argument[] args = template.arguments;
                if (args.empty)
                    {
                    throw new IllegalState($"WebService's \"path\" argument must be specified for \"{child}\"");
                    }

                String path = args[0].value.as(String);
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

                Type<WebService>   serviceType = child.PublicType.as(Type<WebService>);
                ServiceConstructor constructor;
                if (!(constructor := serviceType.defaultConstructor()))
                    {
                    throw new IllegalState($"default constructor is missing for \"{child}\"");
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
                                                            |endpoint \"{child}\"
                                                            );
                                    }
                                defaultEndpoint = new EndpointInfo(method, epid++, wsid);
                                }
                            else
                                {
                                throw new IllegalState($"multiple \"Default\" endpoints on \"{child}\"");
                                }
                            break;

                        case Endpoint:
                            endpoints.add(new EndpointInfo(method, epid++, wsid));
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
                                throw new IllegalState($"multiple \"OnError\" handlers on \"{child}\"");
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

                webServices += new WebServiceInfo(wsid, path, constructor,
                                                  endpoints, defaultEndpoint,
                                                  interceptors, observers,
                                                  onError, route
                                                  );
                wsid++;

                // TODO walk recursively except packages representing imported modules
                // TODO search for session mixins inside of the service
                }
            else if (child.implements(Session))
                {
                sessionMixins += child;
                }
            else if (child.implements(Package), Object pkg := child.isSingleton())
                {
                assert pkg.is(Package);

                // don't scan imported modules
                if (!pkg.isModuleImport())
                    {
                    (wsid, epid) =
                        scanClasses(pkg.as(Package).classes, wsid, epid, webServices, sessionMixins);
                    }
                }
            }
        return wsid, epid;
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