import Server.Handler;

/**
 * A mixin that represents a set of endpoints for a specific URI path.
 *
 * Example:
 *
 *     @web.WebModule
 *     module HelloWorld
 *         {
 *         package web import web.xtclang.org;
 *
 *         @web.WebService("/")
 *         service Hello
 *             {
 *             @web.Get("hello")
 *             @web.Produces("text/plain")
 *             String sayHello()
 *                 {
 *                 return "Hello World";
 *                 }
 *             }
 *         }
 */
@Concurrent
mixin WebService(String path = "/")
        into service
    {
    Request? request;
    Session? session;

    /**
     * Process a received [Request].
     *
     * @param handler  TODO
     * @param request  TODO
     * @param session  TODO
     *
     * @return the [Response] to send back to the caller
     */
    Response process(Handler handler, Request request, Session? session)
        {
        if (this.request != Null)
            {
            // the service is concurrent to allow access to its state
            return TODO new responses.SimpleResponse(InternalServerError);
            }

        this.request  = request;
        this.session  = session;
        try
            {
            // REVIEW CP
            return handler(request);
            }
        catch (Exception e)
            {
            return handleException(e);
            }
        finally
            {
            this.request  = Null;
            this.session  = Null;
            }
        }

    /**
     * Handle an exception that occurred during [Request] processing within this `WebService`, and
     * produce a [Response] that is appropriate to the exception that was raised.
     *
     * @param e  the Exception that occurred during the processing of a [Request]
     *
     * @return the [Response] to send back to the caller
     */
    Response handleException(Exception e)
        {
        return this:service.as(WebApp).handleException(e);
        }
    }
