/**
 * The native HttpServer service implementation.
 */
@Concurrent
service RTServer
        implements HttpServer
    {
    @Override
    void attachHandler(Handler handler)
        {
        TODO("Native");
        }

    @Override
    void send(Object context, Int status, String[] headerNames, String[][] headerValues, Byte[] body)
        {
        TODO("Native");
        }

    @Override
    void close(Exception? cause = Null)
        {
        TODO("Native");
        }

    @Override
    String toString()
        {
        return "HtpServer";
        }

    /**
     * Injectable server.
     */
    static interface HttpServer
            extends Closeable
        {
        /**
         * Attach a handler.
         */
        void attachHandler(Handler handler);

        /**
         * Send a response.
         */
        void send(Object context, Int status, String[] headerNames, String[][] headerValues, Byte[] body);
        }

    /**
     * HttpRequest handler.
     */
    static interface Handler
        {
        void handle(Object context, String uri, String method,
                    String[] headerNames, String[][] headerValues, Byte[] body);
        }
    }