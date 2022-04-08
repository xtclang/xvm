/**
 * A native HTTP server service.
 */
@Concurrent
service RTServer
        implements Closeable
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

    @Override
    void close(Exception? cause = Null)
        {
        TODO("Native");
        }

    static interface Handler
        {
        void handle(Object context, String uri, String method,
                    String[] headerNames, String[][] headerValues, Byte[] body);
        }
    }