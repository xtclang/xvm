/**
 * A representation of an HTTP client.
 * TODO how to secure? need to be able to restrict which algos can be used
 * TODO patch method
 */
interface Client
    {
    // ----- Client configuration ------------------------------------------------------------------

    /**
     * TODO
     */
    Client allow(HostPort|HostPort[]|Protocol|Protocol[] allow);

    /**
     * TODO
     */
    Client deny(HostPort|HostPort[]|Protocol|Protocol[] deny);

    // TODO how to specify client certificate (and chain)
    // authenticate()

    /**
     * TODO
     */
    Client restrictTo(Uri baseURI);

    /**
     * Each request created will include these headers.
     */
    @RO Header defaultHeaders;


    // ---- helpers for simple HTTP methods --------------------------------------------------------

    /**
     * Get the specified resource.
     *
     * @param uri  the resource identifier to get
     *
     * @return the resulting [Response] object
     */
    Response get(String | Uri uri)
        {
        return send(createRequest(uri.is(String) ? new Uri(uri) : uri, GET));
        }

    /**
     * Put the specified resource.
     *
     * @param uri        the resource identifier and contents to put
     * @param bytes      the content of the request body, in bytes
     * @param mediaType  (optional) the media type of the body; defaults to `Json`
     *
     * @return the resulting [Response] object
     */
    Response put(String | Uri uri, Byte[] bytes, MediaType mediaType=Json)
        {
        return send(createRequest(uri.is(String) ? new Uri(uri) : uri, PUT));
        }

    /**
     * Post the specified resource.
     * REVIEW what about a version that takes a (json.Doc json) parameter?
     *
     * @param uri        the resource identifier to post a request to
     * @param bytes      the content of the request body, in bytes
     * @param mediaType  (optional) the media type of the body; defaults to `Json`
     *
     * @return the resulting [Response] object
     */
    Response post(String | Uri uri, Byte[] bytes, MediaType mediaType=Json)
        {
        Request request = createRequest(uri.is(String) ? new Uri(uri) : uri, POST);
        Body body  = request.ensureBody(mediaType);
        body.bytes = bytes;
        return send(request);
        }

    /**
     * Delete the specified resource.
     *
     * @param uri  the resource identifier to delete
     *
     * @return the resulting [Response] object
     */
    Response delete(String | Uri uri)
        {
        return send(createRequest(uri.is(String) ? new Uri(uri) : uri, DELETE));
        }


    // ---- request handling -----------------------------------------------------------------------

    /**
     * Create an new Request that contains the default headers and the specified Uri and HTTP
     * method.
     *
     * @param uri     the [Uri] for the request
     * @param method  the [HttpMethod] for the request
     *
     * @return TODO
     */
    Request createRequest(Uri uri, HttpMethod method);

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
    }