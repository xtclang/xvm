/**
 * A proxy to the native web server.
 */
interface WebServerProxy
    {
    /**
     * Start the server.
     *
     * @param handler  the handler callback that will be called to handle http requests
     */
    void start(Handler handler);

    /**
     * A handler for http requests.
     *
     * This is a function that takes a HttpRequestProxy and a Responder function.
     */
    typedef function void (HttpRequestProxy, Responder) as Handler;

    /**
     * A Responder handles passing a http response back to the native web server.
     * It is effectively a proxy to a native function.
     *
     * @param Int                        the http status code for the response
     * @param Byte[]                     the Byte array that is the response body
     * @param Tuple<String, String[]>[]  an array of Tuples that make up the response header values
     */
    typedef function void (Int, Byte[], Tuple<String, String[]>[]) as Responder;
    }