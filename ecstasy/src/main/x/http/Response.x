/**
 * A http response.
 */
service Response
    {
    /**
     * Send the response back to the caller.
     *
     * @param status        the response http status code
     * @param headerNames   an array of http header names
     * @param headerValues  an array of http header values
     * @param body          the response body
     */
    void send(Int status, String[] headerNames, String[][] headerValues, Byte[] body)
        {
        TODO
        }
    }
