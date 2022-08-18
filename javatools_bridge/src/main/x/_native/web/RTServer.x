import libweb.HttpServer;
import libweb.HttpServer.Handler;

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
    }