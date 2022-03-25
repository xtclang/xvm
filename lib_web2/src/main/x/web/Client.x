/**
 * A representation of an HTTP client.
 */
interface Client
    {
    /**
     * Each request sent will include these headers.
     */
    @RO Header defaultHeaders;

    /**
     * Send a request.
     *
     * @param request  the request to send
     *
     * @return the response
     *
     * TODO document failure modes (does it return a response? or throw?)
     */
    Response send(Request request);

    /**
     * Get the specified resource.
     *
     * @param uri  the resource identifier to get
     */
    Response get(String | URI uri);

    /**
     * Put the specified resource.
     *
     * @param uri  the resource identifier and contents to put
     */
    Response put(String | URI uri);

    /**
     * Post the specified resource.
     *
     * @param uri  the resource identifier to post a request to
     */
    Response post(String | URI uri, Body body);

    /**
     * Delete the specified resource.
     *
     * @param uri  the resource identifier to delete
     */
    Response delete(String | URI uri);
    }
