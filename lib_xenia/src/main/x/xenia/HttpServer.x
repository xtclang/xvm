import net.IPAddress;
import net.URI;

import web.HttpMethod;


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
        void handle(RequestContext context, String uri, String method);
        }

    /**
     * Attach a handler.
     */
    void attachHandler(Handler handler);

    /**
     * Send a response.
     */
    void send(RequestContext context, Int status, String[] headerNames, String[] headerValues, Byte[] body);


    // ----- context attributes --------------------------------------------------------------------

    /**
     * Obtain the IP address that the request was sent from.
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the bytes of the client IP address, in either v4 or v6 form
     */
    Byte[] getClientAddressBytes(RequestContext context);

    /**
     * Obtain the port number on the client that the request was sent from
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the client port number
     */
    UInt16 getClientPort(RequestContext context);

    /**
     * Obtain the IP address that the request was received on.
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the bytes of the server IP address, in either v4 or v6 form
     */
    Byte[] getServerAddressBytes(RequestContext context);

    /**
     * Obtain the port number on the server that the request was received on
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the server port number
     */
    UInt16 getServerPort(RequestContext context);

    /**
     * Obtain the HTTP method name (such as "GET" or "PUT") that is indicated by the request.
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the HTTP method name
     */
    String getMethodString(RequestContext context);

    /**
     * Obtain the HTTP URI that is indicated by the request.
     * REVIEW is this the whole URI? or just the path/query from the request line? the JavaDoc is not clear
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the HTTP URI string
     */
    String getUriString(RequestContext context);

    /**
     * Obtain the HTTP protocol name (such as "HTTP/1.1") that is indicated by the request. In HTTP
     * 1.1, this is part of the "request line", which is the first line of text in the request.
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the HTTP protocol name
     */
    String getProtocolString(RequestContext context);

    /**
     * Obtain all the header names.
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return the array of all header names
     */
    String[] getHeaderNames(RequestContext context);

    /**
     * Obtain all of the values for the specified header name.
     *
     * @param context  the context that was passed to a `Handler` for a request
     * @param name     the case-insensitive header name
     *
     * @return True if there is at least one header for the specified name
     * @return (conditional) an array of one or more values associated with the specified name
     */
    conditional String[] getHeaderValuesForName(RequestContext context, String name);

    /**
     * Obtain all of the bytes in the request body.
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return True if there is a body
     * @return (conditional) an array of `Byte` representing the body content
     */
    conditional Byte[] getBodyBytes(RequestContext context);

    /**
     * Determine if the body contains nested information (e.g. multi-part) with its own headers, etc.
     *
     * @param context  the context that was passed to a `Handler` for a request
     *
     * @return True if there is one or more nested bodies
     * @return (conditional) an array of `context` objects, each representing the one nested body
     */
    conditional RequestContext[] containsNestedBodies(RequestContext context);


    // ----- RequestInfo ---------------------------------------------------------------------------

    /**
     * An object that provides access to information about a request.
     */
    static const RequestInfo(HttpServer server, RequestContext context)
        {
        /**
         * Obtain the IP address that the request was sent from.
         *
         * @return the client IP address, in either v4 or v6 form
         */
        IPAddress getClientAddress()
            {
            return new IPAddress(server.getClientAddressBytes(context));
            }

        /**
         * Obtain the port number on the client that the request was sent from
         *
         * @return the client port number
         */
        UInt16 getClientPort()
            {
            return server.getClientPort(context);
            }

        /**
         * Obtain the IP address that the request was received on.
         *
         * @return the server IP address, in either v4 or v6 form
         */
        IPAddress getServerAddress()
            {
            return new IPAddress(server.getServerAddressBytes(context));
            }

        /**
         * Obtain the port number on the server that the request was received on
         *
         * @return the server port number
         */
        UInt16 getServerPort()
            {
            return server.getServerPort(context);
            }

        /**
         * Obtain the HTTP method name (such as "GET" or "PUT") that is indicated by the request.
         *
         * @return the HTTP method name
         */
        HttpMethod getMethod()
            {
            return HttpMethod.of(server.getMethodString(context));
            }

        /**
         * Obtain the HTTP URI that is indicated by the request.
         *
         * @return the URI from the request
         */
        URI getUri()
            {
            return new URI(server.getUriString(context));
            }

        /**
         * Obtain the HTTP protocol name (such as "HTTP/1.1") that is indicated by the request. In HTTP
         * 1.1, this is part of the "request line", which is the first line of text in the request.
         *
         * @return the HTTP protocol name
         */
        String getProtocolString()
            {
            return server.getProtocolString(context);
            }

        /**
         * Obtain all the header names.
         *
         * @return the array of all header names
         */
        String[] getHeaderNames()
            {
            return server.getHeaderNames(context);
            }

        /**
         * Obtain all of the values for the specified header name.
         *
         * @param name  the case-insensitive header name
         *
         * @return True if there is at least one header for the specified name
         * @return (conditional) an array of one or more values associated with the specified name
         */
        conditional String[] getHeaderValuesForName(String name)
            {
            return server.getHeaderValuesForName(context, name);
            }

        /**
         * Obtain all of the bytes in the request body.
         *
         * @return True if there is a body
         * @return (conditional) an array of `Byte` representing the body content
         */
        conditional Byte[] getBodyBytes()
            {
            return server.getBodyBytes(context);
            }

        /**
         * Determine if the body contains nested information (e.g. multi-part) with its own headers,
         * etc.
         *
         * @return True if there is one or more nested bodies
         * @return (conditional) an array of `context` objects, each representing the one nested
         *         body
         */
        conditional RequestInfo[] containsNestedBodies()
            {
            if (RequestContext[] contexts := server.containsNestedBodies(context))
                {
                return True, new RequestInfo[contexts.size](ctx -> new RequestInfo(server, ctx));
                }

            return False;
            }
        }
    }