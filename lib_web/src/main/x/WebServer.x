/**
 * A web server.
 */
class WebServer
    {
    import ecstasy.io.ByteArrayOutputStream;

    import ecstasy.proxy.HttpRequestProxy;
    import ecstasy.proxy.WebServerProxy;

    import ecstasy.reflect.Parameter;

    import binder.BodyParameterBinder;
    import binder.BindingResult;
    import binder.ParameterBinder;
    import binder.RequestBinderRegistry;

    import codec.MediaTypeCodec;
    import codec.MediaTypeCodecRegistry;

    @Inject Console console;

    /**
     * The proxy to the native web server.
     */
    @Inject WebServerProxy proxy;

    /**
     * The router holding the various endpoints that requests can be routed to.
     */
    private Router router = new Router();

    /**
     * A registry of binders that can bind various attributes of a http request
     * to different parameters of an endpoint method.
     */
    private RequestBinderRegistry binderRegistry = new RequestBinderRegistry();

    /**
     * The registry of codecs for converting request and response bodies to
     * endpoint method parameters. For example deserializing a json request
     * body to a specific Object type.
     */
    private MediaTypeCodecRegistry codecRegistry = new MediaTypeCodecRegistry();

    /**
     * Add all of the Routes from the annotated endpoints in the specified object.
     *
     * @param o  the class with annotated endpoints
     *
     * @return  this WebServer
     */
    <T> WebServer addRoutes(T o)
        {
        router.addRoutes(o);
        return this;
        }

    // ToDo: Methods to add handlers for http response statuses and exceptions

    /**
     * Start the web server.
     */
    void start()
        {
        binderRegistry.addParameterBinder(new BodyParameterBinder(codecRegistry));
        proxy.start(handle);
        }

    /**
     * The request handler that is called by the native web server.
     */
    void handle(HttpRequestProxy req, WebServerProxy.Responder responder)
        {
        try
            {
            URI                 uri      = URI.create(req.uri);
            HttpMethod          method   = HttpMethod.fromName(req.method);
            HttpRequest         httpReq  = new HttpRequest(uri, req.headers, method, req.body);
            List<UriRouteMatch> routes   = router.findClosestRoute(httpReq);
            HttpResponse        httpResp;

            if (routes.size == 1)
                {
                // a single endpoint matches the request
                UriRouteMatch matchedRoute = routes[0];

                httpReq.attributes.add(HttpAttributes.ROUTE, matchedRoute.route);
                httpReq.attributes.add(HttpAttributes.ROUTE_MATCH, matchedRoute);

                // bind values from the request to the endpoint method parameters and execute the method call
                RouteMatch bound  = binderRegistry.bind(matchedRoute, httpReq);
                Tuple      result = bound.execute();

                if (bound.conditionalResult && result.size > 0 && result[0].as(Boolean) == False)
                    {
                    // the method executed is a conditional method that has returned False as the
                    // first element of the Tuple
                    httpResp = new HttpResponse(HttpStatus.NotFound);
                    }
                else
                    {
                    MediaType  mediaType = resolveDefaultResponseContentType(httpReq, bound);
                    httpResp = encodeResponse(result, method, mediaType);
                    }
                }
            else if (routes.size == 0)
                {
                // no endpoints match the request
                // ToDo: should be handled by a 404 status handler if one has been added
                httpResp = new HttpResponse(HttpStatus.NotFound);
                }
            else
                {
                // At this point there are multiple endpoints that match the request
                // ToDo: we should attempt to narrow down multiple results using other rules
                httpResp = new HttpResponse(HttpStatus.MultipleChoices);
                }

            // ToDo: Check the status and execute any status handler for the status

            sendResponse(httpResp, responder);
            }
        catch (Exception e)
            {
            handleException(e, responder);
            }
        }

    /**
     * Process the `Tuple` returned from a request handler into a `HttpResponse`.
     *
     * @param tuple      the `Tuple` of return values from the endpoint method execution
     * @param method     the http request method (i.e. GET, POST, etc)
     * @param mediaType  the media type of the response body
     *
     * @return a HttpResponse
     */
    HttpResponse encodeResponse(Tuple tuple, HttpMethod method, MediaType mediaType)
        {
        HttpResponse httpResp = new HttpResponse();
        httpResp.headers.add("Content-Type", mediaType.name);

        if (tuple.size == 0)
            {
            // method had a void return type so there is no response body
            if (HttpMethod.permitsRequestBody(method))
                {
                // method allows a body so set the length to zero
                httpResp.headers.add("Content-Length", "0");
                }
            return httpResp;
            }

        if (tuple[0].is(HttpResponse))
            {
            // the endpoint returned a HttpResponse so use that as the response
            return tuple[0].as(HttpResponse);
            }

        // Iterate over the return values from the endpoint assigning them to the
        // relevant parts of the request
        for (Int i : [0..tuple.size))
            {
            Object o = tuple[i];
            if (o.is(HttpStatus))
                {
                httpResp.status = o;
                }
            else if (o != Null)
                {
                httpResp.body = o;
                }
            }

        // If there is a body convert it to the requires response media type
        if (httpResp.body != Null)
            {
            if (MediaTypeCodec codec := codecRegistry.findCodec(mediaType))
                {
                httpResp.body = codec.encode(httpResp.body);
                }
            // ToDo: the else should probably be an error/exception
            }

        return httpResp;
        }

    /**
     * Send the response to the native responder.
     *
     * @param response   the http response to write
     * @param responder  the responder function to call to send the response
     */
    void sendResponse(HttpResponse response, WebServerProxy.Responder responder)
        {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        Object? body = response.body;
        if (body.is(Iterable<Char>))
            {
            for (Char c : body)
                {
                out.writeBytes(c.utf8());
                }
            }
        else if (body.is(Byte[]))
            {
            out.writeBytes(body);
            }

        responder(response.status.code, out.bytes, response.headers.toTuples());
        }

    /**
     * Determine the default content type for the response body.
     *
     * @param request  the http request
     * @param route    the route to the endpoint handling the request
     *
     * @return the MediaType of the response body
     */
    MediaType resolveDefaultResponseContentType(HttpRequest request, RouteMatch route)
        {
        MediaType[] accepts = request.accepts;
        for (MediaType mt : accepts)
            {
            if (mt != MediaType.ALL_TYPE && route.canProduce(mt))
                {
                return mt;
                }
            }

        MediaType[] produces = route.produces;
        if (produces.size > 0)
            {
            return produces[0];
            }
        return MediaType.APPLICATION_JSON_TYPE;
        }

    /**
     * Handle and exception and send a response back to the http request caller.
     *
     * @param error      the Exception that occurred
     * @param responder  the responder function to call to send the response
     */
    void handleException(Exception error, WebServerProxy.Responder responder)
        {
        // ToDo: eventually we should allow custom exception handling routes to be specified

        @Inject Console console;
        console.println(error.toString());
        if (error.is(HttpException))
            {
            sendResponse(new HttpResponse(error.status), responder);
            }
        else
            {
            sendResponse(new HttpResponse(HttpStatus.InternalServerError), responder);
            }
        }
    }