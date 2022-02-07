/**
 * An HTTP response.
 * TODO CP: deprecate and remove
 */
service Response
    {
    /**
     * Send the response back to the caller.
     *
     * @param status        the response HTTP status code
     * @param headerNames   an array of HTTP header names
     * @param headerValues  an array of HTTP header values
     * @param body          the response body
     */
    void send(Int status, String[] headerNames, String[][] headerValues, Byte[] body)
        {
        TODO
        }
    }
