import Server.Handler;
import Server.ErrorHandler;
import Server.Interceptor;
import Server.Observer;

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
 *         @web.WebService(Path:/)
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
mixin WebService(Path path)
        into service
        implements Replicable
    {
    // ----- properties ----------------------------------------------------------------------------

    /**
     * The [WebApp] containing this `WebService`. If no `WebApp` is explicitly configured, then the
     * module containing the `WebService` is used.
     */
    WebApp webApp
        {
        @Override
        WebApp get()
            {
            WebApp app;
            if (assigned)
                {
                app = super();
                }
            else
                {
                assert var moduleObject := this:class.baseTemplate.containingModule.ensureClass().isSingleton()
                        as $"Unable to obtain containing module for {this}";
                assert app := moduleObject.is(WebApp) as $"Unable to obtain the WebApp for {this}";
                set(app);
                }

            return app;
            }

        @Override
        void set(WebApp app)
            {
            assert !assigned as $"The WebApp containing this WebService cannot be modified";
            super(app);
            }
        }

    /**
     * The session related to the currently executing handler within this service.
     */
    Session? session;

    /**
     * The request for the currently executing handler within this service.
     */
    Request? request;

    /**
     * The response (if one is known) for the currently executing handler within this service.
     */
    Response? response;


    // ----- processing ----------------------------------------------------------------------------

    /**
     * Bind the specified method as necessary in order to create a [Handler] that will invoke the
     * method.
     *
     * @param method  the method on this WebService to invoke to handle a request
     *
     * @return a Handler
     */
    Handler createHandler(Method<WebService> method)
        {
        TODO
        }

    /**
     * Bind the specified method as necessary in order to create an [Interceptor] that will invoke
     * the method.
     *
     * @param method  the method on this WebService to invoke to intercept a request
     *
     * @return an Interceptor
     */
    Interceptor createInterceptor(Method<WebService> method)
        {
        TODO
        }

    /**
     * Bind the specified method as necessary in order to create an [Observer] that will invoke the
     * method.
     *
     * @param method  the method on this WebService to invoke to observe a request
     *
     * @return an Observer
     */
    Observer createObserver(Method<WebService> method)
        {
        TODO
        }

    /**
     * Bind the specified method as necessary in order to create an [ErrorHandler] that will invoke
     * the method.
     *
     * @param method  the method on this WebService to invoke to handle an error
     *
     * @return an ErrorHandler
     */
    ErrorHandler createErrorHandler(Method<WebService> method)
        {
        TODO
        }


    // ----- processing ----------------------------------------------------------------------------

    /**
     * Process a received [Request].
     *
     * This method is called from the previous step in the routing chain, in order to transfer
     * control into this web service. For the duration of the processing inside this web service,
     * the session and the request will be available as properties on the WebService.
     *
     * @param session  the [Session] to hold onto (so that it's available for the duration of the
     *                 request processing)
     * @param request  the [Request] to hold onto (so that it's available for the duration of the
     *                 request processing)
     * @param handler  the handler to delegate the processing to
     *
     * @return the [Response] to send back to the caller
     */
    Response route(Session session, Request request, Handler handle)
        {
        assert this.request == Null;

        // store the request and session for the duration of the request processing
        this.request  = request;
        this.session  = session;
        this.response = Null;

        try
            {
            return handle(session, request);
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
    Response routeError(Session          session,
                        Request          request,
                        Response?        response,
                        Exception|String error,
                        ErrorHandler     handle,
                       )
        {
        Session?  prevSession  = this.session;
        Request?  prevRequest  = this.request;
        Response? prevResponse = this.response;

        // store the request and session for the duration of the request processing
        this.session  = session;
        this.request  = request;
        this.response = response;

        try
            {
            return handle(session, request, error, response);
            }
        catch (Exception e)
            {
            return unhandledException(e);
            }
        finally
            {
            this.session  = prevSession;
            this.request  = prevRequest;
            this.response = prevResponse;
            }
        }


    // ----- processing ----------------------------------------------------------------------------

    /**
     * Handle an exception that occurred during [Request] processing within this `WebService`, and
     * produce a [Response] that is appropriate to the exception that was raised.
     *
     * @param e  the Exception that occurred during the processing of a [Request]
     *
     * @return the [Response] to send back to the caller
     */
    protected Response handleException(Exception e)
        {
        Session      session = this.session ?: assert;
        Request      request = this.request ?: assert;
        ErrorHandler handle  = this:module.as(WebApp).allocateErrorHandler(request, session, response);
        return handle^(session, request, e, Null);
        }

    /**
     * Handle an exception that occurred during the handling of an error. Since we're already
     * supposed to be dealing with an error, an exception at this point should be considered
     * unrecoverable; the processing should to be minimal and safe, and the result should clearly
     * indicate to the user agent that an internal server error has occurred.
     *
     * @param e  the Exception that occurred during the (mis-)handling of an error
     *
     * @return the [Response] to send back to the caller
     */
    protected Response unhandledException(Exception e)
        {
        return new responses.SimpleResponse(InternalServerError);
        }
    }
