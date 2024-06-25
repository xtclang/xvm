import requests.SimpleRequest;


/**
 * A representation of a web client.
 * TODO how to secure? need to be able to restrict which algorithms can be used
 * TODO patch method
 */
interface Client {
    // ----- Client configuration ------------------------------------------------------------------

    /**
     * Create a "restricted" client that allows communication *only* to the specified ports and/or
     * protocols.
     *
     * @param hostPorts    the [HostPort]s that the new Client is allowed to use; if empty, no
     *                     restrictions are placed
     * @param protocols    the [Protocol]s that the new Client is allowed to use; if empty, no
     *                     restrictions are placed
     */
    Client! allow(HostPort|HostPort[] hostPorts, Protocol|Protocol[] protocols) {
        Client restricted = new RestrictedClient(this, Allow, hostPorts, protocols);
        return &restricted.maskAs(Client);
    }

    /**
     * Create a "restricted" client that disallows communication to any the specified ports and/or
     * protocols.
     *
     * @param hostPorts    the [HostPort]s that the new Client is disallowed to use; if empty, no
     *                     restrictions are placed
     * @param protocols    the [Protocol]s that the new Client is disallowed to use; if empty, no
     *                     restrictions are placed
     */
    Client! deny(HostPort|HostPort[] hostPorts, Protocol|Protocol[] protocols) {
        Client restricted = new RestrictedClient(this, Deny, hostPorts, protocols);
        return &restricted.maskAs(Client);
    }

    /**
     * Create a `Client` that restricts access to URIs "under" the specified [Uri].
     */
    Client! restrictTo(Uri baseUri) {
        String   host = baseUri.host ?: assert as "host must be specified";
        UInt16   port = baseUri.port ?: 0;

        Protocol|Protocol[] protocol = [];
        if (String scheme ?= baseUri.scheme) {
            assert protocol := Protocol.byProtocolString.get(scheme) as $"unknown protocol: {scheme}";
        }

        Client restricted = new RestrictedClient(this, Allow, (host, port), protocol);
        return &restricted.maskAs(Client);
    }

    /**
     * Create a `Client` that is connected (and limited) to the specified [Uri].
     */
    Client connectTo(Uri site) {
        TODO
    }

    /**
     * The registry for codecs and media types.
     */
    Registry registry;

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
    ResponseIn get(String | Uri uri) {
        RequestOut request = createRequest(GET, uri.is(String) ? new Uri(uri) : uri);
        return send(request);
    }

    /**
     * Put the specified resource.
     *
     * @param uri        the resource identifier and contents to put
     * @param content    the content of the request body
     * @param mediaType  (optional) the media type of the body
     *
     * @return the resulting [Response] object
     */
    ResponseIn put(String | Uri uri, Object content, MediaType? mediaType=Null) {
        RequestOut request = createRequest(PUT, uri.is(String) ? new Uri(uri) : uri, content, mediaType);
        return send(request);
    }

    /**
     * Post the specified resource.
     * REVIEW what about a version that takes a (json.Doc json) parameter?
     *
     * @param uri        the resource identifier to post a request to
     * @param content    the content of the request body
     * @param mediaType  (optional) the media type of the body
     *
     * @return the resulting [Response] object
     */
    ResponseIn post(String | Uri uri, Object content, MediaType? mediaType=Null) {
        RequestOut request = createRequest(POST, uri.is(String) ? new Uri(uri) : uri, content, mediaType);
        return send(request);
    }

    /**
     * Delete the specified resource.
     *
     * @param uri  the resource identifier to delete
     *
     * @return the resulting [Response] object
     */
    ResponseIn delete(String | Uri uri) {
        RequestOut request = createRequest(DELETE, uri.is(String) ? new Uri(uri) : uri);
        return send(request);
    }


    // ---- request handling -----------------------------------------------------------------------

    /**
     * Create an new Request that contains the default headers and the specified Uri and HTTP
     * method.
     *
     * @param method     the [HttpMethod] for the request
     * @param uri        the [Uri] for the request
     * @param content    (optional) the content of the request body
     * @param mediaType  (optional) the media type of the body
     *
     * @return a new Request object
     */
    RequestOut createRequest(HttpMethod method, Uri uri, Object? content=Null, MediaType? mediaType=Null) {
        SimpleRequest request = new SimpleRequest(this, method, uri);
        defaultHeaders.entries.forEach(entry -> request.add(entry));

        if (content != Null || mediaType != Null) {
            assert method.body != Forbidden;

            if (mediaType == Null) {
                if (!(mediaType := registry.inferMediaType(content))) {
                    throw new IllegalArgument($"Unable to find MediaType for: {&content.actualType}");
                }
            }
            request.header.put(Header.ContentType, mediaType.text);
            request.ensureBody(mediaType).from(content?);
        }
        return request;
    }

    /**
     * Password callback used for authentication.
     */
    typedef function (String name, String password)(String) as PasswordCallback;

    /**
     * Send a request.
     *
     * @param request   the request to send
     * @param callback  (optional) a function that provides used name and password if authentication
     *                  is required
     *
     * @return the response
     *
     * @throws TimedOut   if the request has timed out
     * @throws Exception  if the request failed to be sent or get a response for any other reason
     */
    ResponseIn send(RequestOut request, PasswordCallback? callback = Null);

    /**
     * Represents a low-level web Connector.
     */
    static interface Connector {
        /**
         * Default headers to include into every request.
         */
        (String[] defaultHeaderNames, String[] defaultHeaderValues) getDefaultHeaders();

        /**
         * Send a request.
         */
        (Int statusCode, String[] responseHeaderNames, String[] responseHeaderValues, Byte[] responseBytes)
                sendRequest(String method, String uri,
                            String[] headerNames, String[] headerValues, Byte[] bytes);
    }
}