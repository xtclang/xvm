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
    // TODO HandlerFactory configureRunningWebApp()

    /**
     * Collect all roots declared by this Module and instantiate corresponding WebServices.
     */
    immutable Map<String, WebService> collectRoots_()
        {
        import ecstasy.reflect.Annotation;

        Map<String, WebService> roots = new HashMap();

        Module webModule = this;
        for (Class child : webModule.classes)
            {
            if (child.implements(WebService), Struct structure := child.allocate())
                {
                WebService webService = child.instantiate(structure).as(WebService);

                String path = webService.path;
                if (roots.contains(path))
                    {
                    // TODO: how to report a duplicate path?
                    }
                else
                    {
                    roots.put(path, &webService.maskAs(WebService));
                    }
                }
            }

        return roots.makeImmutable();
        }

    /**
     * Create a session object.
     * TODO
     *
     * @return the new `Session` object
     */
    Session createSession()
        {
        TODO
        }

    /**
     * Handle an exception that occurred during [Request] processing within this `WebApp`, and
     * produce a [Response] that is appropriate to the exception that was raised.
     *
     * @param e  the Exception that occurred during the processing of a [Request]
     *
     * @return the [Response] to send back to the caller
     */
    Response handleException(Exception e)
        {
        // the exception needs to be logged
        return TODO new responses.SimpleResponse(e.is(RequestAborted) ? e.status : InternalServerError);
        }
    }
