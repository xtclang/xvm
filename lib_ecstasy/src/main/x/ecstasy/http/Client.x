/**
 * An HTTP client service.
 * TODO CP: deprecate and remove
 */
const Client(Int connectionTimeout, Redirect redirect, Int priority)
    {
    /**
     * Send an HTTP request.
     *
     * @param uri           the URI being requested
     * @param method        the request method, e.g. GET, POST, PUT, DELETE, etc
     * @param headerNames   the request header names that correspond to header values in the same
     *                      position in the headerValues array
     * @param headerValues  the request header values that correspond to the header name in the
     *                      same position in the headerNames array
     * @param body          the request body
     *
     * @return the response status (a valid HTTP status code)
     * @return the response header names that correspond to header values in the same
     *         position in the headerValues array
     * @return the response header values that correspond to the header name in the
     *         same position in the headerNames array
     * @return the response body
     */
    (Int, String[], String[][], Byte[]) send(String uri, String method = "GET", Array<String> headerNames = [], Array<Array<String>> headerValues = [], Array<Byte> body = [])
        {
        TODO
        }

    enum Redirect
        {
        /**
         * Never redirect.
         */
        Never,
        /**
         * Always redirect.
         */
        Always,
        /**
         * Always redirect, except from HTTPS URLs to HTTP URLs.
         */
        Normal
        }

    }