/**
 * A native HTTP server service.
 */
@Concurrent
service RTServer
    {
    void attachHandler(Handler handler)
        {
        TODO("Native");
        }

    /**
     * Send a response.
     */
    void send(Object context, Int status, String[] headerNames, String[][] headerValues, Byte[] body)
        {
        TODO("Native");
        }

    static interface Handler
        {
        void handle(Object context, String uri, String method,
                    String[] headerNames, String[][] headerValues, Byte[] body);
        }
    }