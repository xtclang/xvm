import Server.Handler;
import Server.ErrorHandler;

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
        implements Replicable
    {
    Request?  request;
    Session?  session;
    Response? response;

    /**
     * Process a received [Request].
     *
     * @param handler  the handler to delegate the processing to
     * @param request  the [Request] to hold onto (so that it's available for the duration of the
     *                 request processing)
     * @param session  the [Session] to hold onto (so that it's available for the duration of the
     *                 request processing)
     *
     * @return the [Response] to send back to the caller
     */
    Response route(Handler handle, Request request, Session? session)
        {
        if (this.request != Null)
            {
            // the service is onliy marked as @Concurrent to allow access to its state for
            // manageability purposes; it cannot execute more than one handler at a time
            return TODO new responses.SimpleResponse(InternalServerError);  // TODO CP
            }

        // store the request and session for the duration of the request processing
        this.request  = request;
        this.session  = session;
        this.response = Null;

        try
            {
            // REVIEW how to weave in parameter binding etc.
            return handle(request);
            }
        catch (Exception e)
            {
            return handleException(e);
            }
        finally
            {
            this.request  = Null;
            this.session  = Null;
            this.response = Null;
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
        Request request = this.request ?: assert;
        ErrorHandler handle = this:module.as(WebApp).allocateErrorHandler(request, session, response);
        return handle^(request, Null, e);
        }

    /**
     * Process an error.
     *
     * @param handle    the error handler to delegate to
     * @param request   the request that failed
     * @param session   the session related to the request that failed
     * @param response  the response, if one is already known by this point, otherwise null
     * @param error     the exception or string description of an internal error
     *
     * @return the [Response] to send back to the caller
     */
    Response routeError(ErrorHandler     handle,
                        Request          request,
                        Session?         session,
                        Response?        response,
                        Exception|String error)
        {
        Request?  prevRequest  = this.request;
        Session?  prevSession  = this.session;
        Response? prevResponse = this.response;

        // store the request and session for the duration of the request processing
        this.request  = request;
        this.session  = session;
        this.response = response;

        try
            {
            return handle(request, response, error);
            }
        catch (Exception e)
            {
            return TODO new responses.SimpleResponse(InternalServerError); // TODO
            }
        finally
            {
            this.request  = prevRequest;
            this.session  = prevSession;
            this.response = prevResponse;
            }
        }
    }
