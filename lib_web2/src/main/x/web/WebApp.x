import routing.Catalog;
import routing.Catalog.EndpointInfo;
import routing.Catalog.MethodInfo;
import routing.Catalog.WebServiceInfo;

import routing.UriTemplate;


/**
 * The `@WebApp` annotation is used to mark a module as being a web-application module. It can
 * contain any number of discoverable HTTP endpoints.
 *
 * Within an `@WebApp`-annotated module, a number of `@Inject` injections are assumed to be
 * supported by the container:
 *
 * |    Type      |    Name    | Description                        |
 * |--------------|------------|------------------------------------|
 * | Server       |
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

        import ecstasy.reflect.Annotation;
        import ecstasy.reflect.Argument;

        WebServiceInfo[] webServices   = new WebServiceInfo[];
        Class[]          sessionMixins = new Class[];
        Int              wsid          = 0;
        Int              epid          = 0;

        Module webModule = this;
        for (Class child : webModule.classes)
            {
            (Class baseClass, Annotation[] annos) = child.deannotate();

            if (Annotation serviceAnno := annos.any(anno->anno.mixinClass == WebService))
                {
                String     path = "";
                Argument[] args = serviceAnno.arguments;
                if (!args.empty)
                    {
                    path = args[0].value.as(Path).toString();
                    }

                Type<WebService> serviceType = child.PublicType.as(Type<WebService>);

                Catalog.ServiceConstructor constructor;
                if (!(constructor := serviceType.defaultConstructor()))
                    {
                    throw new IllegalState("default WebService constructor is missing for {serviceType}");
                    }

                EndpointInfo[] endpoints       = new EndpointInfo[];
                EndpointInfo?  defaultEndpoint = Null;
                MethodInfo[]   interceptors    = new MethodInfo[];
                MethodInfo[]   observers       = new EndpointInfo[];
                MethodInfo?    onError         = Null;
                MethodInfo?    route           = Null;

                for (Method<WebService, Tuple, Tuple> method : serviceType.methods)
                    {
                    switch (method.is(_))
                        {
                        case Default:
                            if (defaultEndpoint == Null)
                                {
                                defaultEndpoint = new EndpointInfo(method, -1, wsid);
                                }
                            else
                                {
                                throw new IllegalState("multiple \"Default\" endpoints on {serviceType}");
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
                                throw new IllegalState("multiple \"OnError\" handlers on {serviceType}");
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
                }
            else if (baseClass.PublicType.isA(Type<Session>))
                {
                sessionMixins += baseClass;
                }
            }

        return new Catalog(this, webServices, sessionMixins);
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
        // the exception needs to be logged
        return new responses.SimpleResponse(error.is(RequestAborted) ? error.status : InternalServerError);
        }
    }