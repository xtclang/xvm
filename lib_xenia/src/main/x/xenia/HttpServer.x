/**
 * An injectable server.
 */
interface HttpServer
        extends Closeable
    {
    /**
     * HttpRequest handler.
     */
    static interface Handler
        {
        void handle(immutable Object context, String uri, String method,
                    String[] headerNames, String[][] headerValues, Byte[] body);
        }

    /**
     * Attach a handler.
     */
    void attachHandler(Handler handler);

    /**
     * Send a response.
     */
    void send(immutable Object context, Int status, String[] headerNames, String[][] headerValues, Byte[] body);


    // ----- context attributes --------------------------------------------------------------------

    /**
     * Obtain the IP address that the request was sent from.
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the client IP address, in either v4 or v6 form
     */
    IPAddress getClientAddress(immutable Object context)
        {
        return new IPAddress(getClientAddressBytes(context));
        }

    /**
     * Obtain the IP address that the request was sent from.
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the bytes of the client IP address, in either v4 or v6 form
     */
    Byte[] getClientAddressBytes(immutable Object context);

    /**
     * Obtain the port number on the client that the request was sent from
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the client port number
     */
    UInt16 getClientPort(immutable Object context);

    /**
     * Obtain the IP address that the request was received on.
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the server IP address, in either v4 or v6 form
     */
    IPAddress getServerAddress(immutable Object context)
        {
        return new IPAddress(getServerAddressBytes(context));
        }

    /**
     * Obtain the IP address that the request was received on.
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the bytes of the server IP address, in either v4 or v6 form
     */
    Byte[] getServerAddressBytes(immutable Object context);

    /**
     * Obtain the port number on the server that the request was received on
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the server port number
     */
    UInt16 getServerPort(immutable Object context);

    /**
     * Obtain the HTTP method name (such as "GET" or "PUT") that is indicated by the request.
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the HTTP method name
     */
    HttpMethod getMethod(immutable Object context)
        {
        return HttpMethod.of(getMethodString(context));
        }

    /**
     * Obtain the HTTP method name (such as "GET" or "PUT") that is indicated by the request.
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the HTTP method name
     */
    String getMethodString(immutable Object context);

    /**
     * Obtain the HTTP URI that is indicated by the request.
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the URI from the request
     */
    URI getUri(immutable Object context)
        {
        return new URI(getUriString(context));
        }

    /**
     * Obtain the HTTP URI that is indicated by the request.
     * REVIEW is this the whole URI? or just the path/query from the request line? the JavaDoc is not clear
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the HTTP URI string
     */
    String getUriString(immutable Object context);

    /**
     * Obtain the HTTP protocol name (such as "HTTP/1.1") that is indicated by the request. In HTTP
     * 1.1, this is part of the "request line", which is the first line of text in the request.
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the HTTP protocol name
     */
    String getProtocolString(immutable Object context);

    /**
     * Obtain the number of header name/value pairs
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the the number of header name/value pairs
     */
    Int getHeaderCount(immutable Object context);

    /**
     * Obtain the specified header name and value.
     *
     * @param context  the context that was passed to a `Handler` for a request
     * @param index    a value between `0` (inclusive) and `getHeaderCount` (exclusive)
     *
     * @return the name and value of the specified header
     */
    (String name, String value) getHeader(immutable Object context, Int index)

    /**
     * Obtain all of the values for the specified header name.
     *
     * @param context  the context that was passed to a `Handler` for a request
     * @param name     the case-insensitive header name
     *
     * @return True if there is at least one header for the specified name
     * @return (conditional) an array of one or more values associated with the specified name
     */
    conditional String[] getHeaderValuesForName(immutable Object context, String name);

    /**
     * Obtain all of the bytes in the request body.
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return True if there is a body
     * @return (conditional) an array of `Byte` representing the body content
     */
    conditional Byte[] getBodyBytes(immutable Object context);

    /**
     * Determine if the body contains nested information (e.g. mult-part) with its own headers, etc.
     * REVIEW GG
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return True if there is one or more nested bodies
     * @return (conditional) an array of `context` objects, each representing the one nested body
     */
    conditional immutable Object[] containsNestedBodies(immutable Object context);
    }