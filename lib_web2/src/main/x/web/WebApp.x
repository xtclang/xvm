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
        return new responses.SimpleResponse(error.is(RequestAborted) ? error.status : InternalServerError);
        }
    }
