import Catalog.EndpointInfo;
import Catalog.WebServiceInfo;

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
        // TODO walk through all top level classes (and go into imported modules) to find all of the
        //      WebService classes. remember to set the `webApp` property on each one to this WebApp

        import ecstasy.reflect.Annotation;
        import ecstasy.reflect.Argument;

        EndpointInfo[]   endpoints = new EndpointInfo[];
        WebServiceInfo[] services  = new WebServiceInfo[];

        Module webModule = this;
        for (Class child : webModule.classes)
            {
            (_, Annotation[] annos) = child.deannotate();

            Annotation serviceAnno;
            if (!(serviceAnno := annos.any(anno->anno.mixinClass == WebService)))
                {
                continue;
                }

            String     root = "";
            Argument[] args = serviceAnno.arguments;
            if (!args.empty)
                {
                root = args[0].value.as(Path).toString();
                }

            Type<WebService> serviceType = child.PublicType.as(Type<WebService>);

            Catalog.ServiceConstructor constructor;
            if (!(constructor := serviceType.defaultConstructor()))
                {
                // TODO how to report an absence of a default constructor?
                continue;
                }

            Int wsId = services.size;

            for (Method<WebService, Tuple, Tuple> endpoint : serviceType.methods)
                {
                if (!endpoint.is(Endpoint))
                    {
                    continue;
                    }

                Int epId = endpoints.size;
                endpoints.add(new EndpointInfo(endpoint, epId, wsId));
                }

            WebServiceInfo wsInfo = TODO;
            }

        return TODO;
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
     * @param error     the exception thrown, or the error description
     * @param response  the response, iff a response is known at the time that the error occurred
     *
     * @return the [Response] to send back to the caller
     */
    Response handleUnhandledError(Session? session, Request request, Exception|String error, Response? response)
        {
        // the exception needs to be logged
        return TODO new responses.SimpleResponse(error.is(RequestAborted) ? error.status : InternalServerError);
        }
    }