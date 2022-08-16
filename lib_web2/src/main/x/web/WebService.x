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
     * The error handler within this `WebService`, or `Null` if there is none or none has been
     * configured.
     */
    ErrorHandler? errorHandler;

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
     * Process a received [Request]. This is invoked by the server in order to transfer control to
     * an "EndPoint Handler".
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
            return reportError(e);
            }
        finally
            {
            this.request  = Null;
            this.session  = Null;
            this.response = Null;
            }
        }

    /**
     * Allow user code on the web service to handle an error condition during [Request] processing.
     *
     * @param error  the `Exception` or description of an error that occurred during the processing
     *               of a [Request]
     *
     * @return the [Response] to send back to the caller
     */
    Response reportError(String|Exception error)
        {
        Session session = this.session ?: assert;
        Request request = this.request ?: assert;
        return errorHandler?(session, request, error, response)
                : webApp.handleUnhandledError(session, request, error, response);
        }
    }
