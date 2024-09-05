/**
 * A mixin that represents a set of endpoints for a specific URI path.
 *
 * Example:
 *
 *     @web.WebModule
 *     module HelloWorld {
 *         package web import web.xtclang.org;
 *
 *         @web.WebService("/")
 *         service Hello {
 *             @web.Get("hello")
 *             @web.Produces(Text)
 *             String sayHello() {
 *                 return "Hello World";
 *             }
 *         }
 *     }
 */
mixin WebService(String path)
        into service {
    // ----- properties ----------------------------------------------------------------------------

    /**
     * The [WebApp] containing this `WebService`. If no `WebApp` is explicitly configured, then the
     * module containing the `WebService` is used.
     */
    WebApp webApp {
        @Override
        WebApp get() {
            WebApp app;
            if (assigned) {
                app = super();
            } else {
                assert val moduleObject := this:class.baseTemplate.containingModule.ensureClass().isSingleton()
                        as $"Unable to obtain containing module for {this}";
                assert app := moduleObject.is(WebApp) as $"Unable to obtain the WebApp for {this}";
                set(app);
            }

            return app;
        }

        @Override
        void set(WebApp app) {
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
    RequestIn? request;

    /**
     * The function that represents a WebService constructor.
     */
    typedef function WebService() as Constructor;


    // ----- processing ----------------------------------------------------------------------------

    /**
     * Process a received [RequestIn]. This is invoked by the server in order to transfer control to
     * an "EndPoint Handler".
     *
     * This method is called from the previous step in the routing chain, in order to transfer
     * control into this web service. For the duration of the processing inside this web service,
     * the session and the request will be available as properties on the WebService.
     *
     * @param session  the [Session] to hold onto (so that it's available for the duration of the
     *                 request processing)
     * @param request  the [RequestIn] to hold onto (so that it's available for the duration of the
     *                 request processing)
     * @param handler  the handler to delegate the processing to
     * @param onError  (optional) the error handler to delegate the error processing to
     *
     * @return the [ResponseOut] to send back to the caller
     */
    ResponseOut route(Session session, RequestIn request, Handler handle, ErrorHandler? onError) {
        assert this.request == Null;

        // store the request and session for the duration of the request processing
        this.request = request;
        this.session = session;

        try {
            return handle(session, request).freeze(True);
        } catch (RequestAborted e) {
            return e.makeResponse();
        } catch (Exception e) {
            return onError?(session, request, e).freeze(True) : throw e;
        } finally {
            this.request = Null;
            this.session = Null;
        }
    }
}