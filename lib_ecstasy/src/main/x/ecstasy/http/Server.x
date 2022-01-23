/**
 * A http server service.
 *
 * @param port     the port to bind the web server to
 * @param handler  the Handler that will requests
 */
@Concurrent service Server(Int port, Handler handler)
    {
    /**
     * Start the web server.
     */
    void start()
        {
        TODO
        }

    /**
     * Stop the web server.
     */
    void stop()
        {
        TODO
        }

    /**
     * @return True if the web server is running, otherwise False
     */
    Boolean isRunning()
        {
        TODO
        }

    /**
     * An interface implemented by http request handlers.
     */
    static interface Handler
        {
        /**
         * Handle a http request.
         *
         * @param response  the http response
         */
        void handle(String uri, String method, String[] headerNames, String[][] headerValues,
                    Byte[] body, Response response);
        }
    }