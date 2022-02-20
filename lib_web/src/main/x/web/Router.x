import ecstasy.reflect.Parameter;

import binder.BodyParameterBinder;
import binder.BindingResult;
import binder.ParameterBinder;
import binder.RequestBinderRegistry;

import codec.MediaTypeCodec;
import codec.MediaTypeCodecRegistry;

/**
 * A router that can work out the routes for a given HTTP method and URI.
 */
@Concurrent
class Router
        implements Freezable
    {
    construct()
        {
        construct Router(new Array(), new Array(), new Array());
        }

    construct (UriRoute[] routes, PreProcessor[] preProcessors, PostProcessor[] postProcessors)
        {
        this.routes         = routes;
        this.preProcessors  = preProcessors;
        this.postProcessors = postProcessors;
        this.codecRegistry  = new MediaTypeCodecRegistry();
        this.binderRegistry = new RequestBinderRegistry();

        this.binderRegistry.addParameterBinder(new BodyParameterBinder(codecRegistry));
        }

    /**
     * The routes this Router can route requests to.
     */
    private Array<UriRoute> routes;

    private PreProcessor[] preProcessors;

    private PostProcessor[] postProcessors;

    /**
     * A registry of binders that can bind various attributes of an HTTP request
     * to different parameters of an endpoint method.
     */
    private RequestBinderRegistry binderRegistry;

    /**
     * The registry of codecs for converting request and response bodies to
     * endpoint method parameters. For example, deserializing a json request
     * body to a specific Object type.
     */
    private MediaTypeCodecRegistry codecRegistry;


    // ----- Freezable interface -------------------------------------------------------------------

    @Override
    immutable Router freeze(Boolean inPlace = False)
        {
        if (&this.isImmutable)
            {
            return this.as(immutable Router);
            }

        if (inPlace)
            {
            this.routes         = this.routes.freeze(inPlace);
            this.preProcessors  = this.preProcessors.freeze(inPlace);
            this.postProcessors = this.postProcessors.freeze(inPlace);
            return this.makeImmutable();
            }

        return new Router(this.routes.freeze(inPlace),
                this.preProcessors.freeze(inPlace),
                this.postProcessors.freeze(inPlace)).makeImmutable();
        }

    /**
     * Add all of the annotated endpoints in the specified web-service.
     *
     * @param webService  the web-service with annotated endpoints
     * @param rootPath    the root path prefix for all endpoints
     */
    void addRoutes(Object webService, String rootPath = "", PreProcessor[] preProcessors = [], PostProcessor[] postProcessors = [])
        {
        Type type;
        Function<<>, <Object>> constructor;

        if (webService.is(Type))
            {
            type = webService;
            assert:arg constructor := type.defaultConstructor();
            }
        else
            {
            type = &webService.actualType;
            constructor = () -> webService;
            }

        if (rootPath.size > 0)
            {
            if (!rootPath.startsWith('/'))
                {
                rootPath = "/" + rootPath;
                }

            if (rootPath.endsWith('/') && rootPath.size > 1)
                {
                rootPath = rootPath[0..rootPath.size-1);
                }
            }

        for (Method<Object, Tuple, Tuple> endpoint : type.methods)
            {
            if (endpoint.is(HttpEndpoint))
                {
                ExecutableFunction executable = new SimpleExecutableFunction(endpoint, constructor);
                MediaType[]        produces   = new Array<MediaType>();
                MediaType[]        consumes   = new Array<MediaType>();
                String             path       = formatPath(rootPath, endpoint.path);
                UriMatchTemplate   template   = UriMatchTemplate.from(path);

                if (endpoint.is(Produces))
                    {
                    produces.add(new MediaType(endpoint.mediaType));
                    }

                if (endpoint.is(Consumes))
                    {
                    consumes.add(new MediaType(endpoint.mediaType));
                    }

                routes.add(new DefaultUriRoute(endpoint.httpMethod, template,
                        executable, consumes, produces, preProcessors, postProcessors));
                }
            }
            routes = routes.sorted();
        }

    private String formatPath(String rootPath, String path)
        {
        if (path == "" || path == "/")
            {
            return rootPath;
            }

        if (path.endsWith('/'))
            {
            path = path[0..rootPath.size-1);
            }

        if (path.startsWith('/'))
            {
            return rootPath + path;
            }

        return rootPath + "/" + path;
        }

    /**
     * Finds the closest matching routes for the given request.
     *
     * @param req  the request
     *
     * @return a list of possible routes
     */
    private List<UriRouteMatch> findClosestRoute(HttpRequest req)
        {
        HttpMethod  method        = req.method;
        Boolean     permitsBody   = method.permitsRequestBody;
        MediaType?  contentType   = req.contentType;
        MediaType[] acceptedTypes = req.accepts;

        // find the routes matching the URI path filtered on matching media types
        List<UriRouteMatch> matches = findRoutes(method, req)
                .filter(match -> (!permitsBody || match.canConsume(contentType))
                                 && match.canProduce(acceptedTypes), new Array())
                .as(List<UriRouteMatch>);

        if (matches.size <= 1)
            {
            // only one or zero matches so we're done
            return matches;
            }

        if (!acceptedTypes.empty)
            {
            // take the highest priority accepted type
            MediaType           mediaType    = acceptedTypes[0];
            List<UriRouteMatch> mostSpecific = matches.filter(
                    match -> match.canProduce(mediaType), new Array()).as(List<UriRouteMatch>);

            if (!mostSpecific.empty || !acceptedTypes.contains(MediaType.ALL_TYPE))
                {
                matches = mostSpecific;
                }
            }

        if (matches.size > 1 && permitsBody)
            {
            List<UriRouteMatch> explicitlyConsumedRoutes = new Array();
            List<UriRouteMatch> consumesRoutes           = new Array();

            for (UriRouteMatch match: matches)
                {
                MediaType explicitType = contentType ?: MediaType.ALL_TYPE;
                if (match.explicitlyConsumes(explicitType))
                    {
                    explicitlyConsumedRoutes.add(match);
                    }

                if (explicitlyConsumedRoutes.empty && match.canConsume(contentType))
                    {
                    consumesRoutes.add(match);
                    }
                }
            matches = explicitlyConsumedRoutes.empty ? consumesRoutes : explicitlyConsumedRoutes;
            }

        if (matches.size > 1)
            {
            List<UriRouteMatch> closestMatches = new Array();
            Int                 variableCount  = 0;
            Int                 rawLength      = 0;

            for (Int i : [0..matches.size))
                {
                UriRouteMatch    match    = matches[i];
                UriMatchTemplate template = match.route.uriMatchTemplate;
                Int              variable = template.variableCount;
                Int              raw      = template.rawLength;

                if (i == 0)
                    {
                    variableCount = variable;
                    rawLength     = raw;
                    }

                if (variable > variableCount || raw < rawLength)
                    {
                    break;
                    }
                closestMatches.add(match);
                }
            matches = closestMatches;
            }

        return matches;
        }

    /**
     * Find the matching routes for an HTTP method, URI and request.
     *
     * @param method  the requested HTTP method
     * @param req     the HttpRequest
     */
    List<UriRouteMatch> findRoutes(HttpMethod method, HttpRequest req)
        {
        UriRouteMatch[] matches = new Array<UriRouteMatch>();
        URI             uri     = req.uri;
        for (UriRoute route : routes)
            {
            if (route.matchesMethod(method), UriRouteMatch match := route.match(uri))
                {
                matches.add(match);
                }
            }
        return matches;
        }

    /**
     * Handle an HTTP request.
     */
    (Int, String[], String[][], Byte[]) handle(String uri, String methodName, String[] headerNames,
            String[][] headerValues, Byte[] body)
        {
        @Inject Console console;

        try
            {
            HttpHeaders         headers = new HttpHeaders(headerNames, headerValues);
            HttpMethod          method  = HttpMethod.fromName(methodName);
            HttpRequest         httpReq = new HttpRequest(new URI(uri), headers, method, body);
            List<UriRouteMatch> routes  = findClosestRoute(httpReq);
            HttpResponse        response;

            if (routes.size == 1)
                {
                // a single endpoint matches the request
                UriRouteMatch matchedRoute = routes[0];

                httpReq.attributes.add(HttpAttributes.ROUTE, matchedRoute.route);
                httpReq.attributes.add(HttpAttributes.ROUTE_MATCH, matchedRoute);

                // bind values from the request to the endpoint method parameters and execute the method call
                function void () fn     = matchedRoute.createFunction();
                RouteMatch       bound  = binderRegistry.bind(fn, matchedRoute, httpReq);
                Tuple            result = bound.execute(fn);

                if (bound.conditionalResult && result.size > 0 && result[0].as(Boolean) == False)
                    {
                    // the method executed is a conditional method that has returned False as the
                    // first element of the Tuple
                    // ToDo: should be handled by a 404 status handler if one has been added
                    response = new HttpResponse(HttpStatus.NotFound);
                    }
                else
                    {
                    MediaType[] accepts   = httpReq.accepts;
                    MediaType   mediaType = bound.resolveDefaultResponseContentType(accepts);
                    Int         index     = bound.conditionalResult ? 1 : 0;

                    response = HttpResponse.encodeResponse(result, index, method, mediaType);

                    // If there is a body convert it to the required response media type
                    if (response.body != Null && !response.body.is(Byte[]))
                        {
                        if (MediaTypeCodec codec := codecRegistry.findCodec(mediaType))
                            {
                            response.body = codec.encode(response.body);
                            }
                        // ToDo: the else should probably be an error/exception
                        }
                    }
                }
            else if (routes.size == 0)
                {
                // no endpoints match the request
                // ToDo: should be handled by a 404 status handler if one has been added
                response = new HttpResponse(HttpStatus.NotFound);
                }
            else
                {
                // At this point there are multiple endpoints that match the request
                // ToDo: we should attempt to narrow down multiple results using other rules
                response = new HttpResponse(HttpStatus.MultipleChoices);
                }

            // ToDo: Check the status and execute any status handler for the status

            return response.asTuple();
            }
        catch (Exception error)
            {
            console.println($"Caught exception handling request for {uri}. {error}");
            // ToDo: should be handled by a 500 status handler if one has been added
            if (error.is(HttpException))
                {
                return (error.status.code, [], [], error.toString().utf8());
                }
            else
                {
                return (HttpStatus.InternalServerError.code, [], [], error.toString().utf8());
                }
            }
        }

    // ----- inner class: DefaultUriRoute ----------------------------------------------------------

    /**
     * The default implementation of a UriRoute.
     */
    protected static const DefaultUriRoute
            implements UriRoute
            implements Stringable
        {
        construct(HttpMethod            httpMethod,
                  UriMatchTemplate      uriMatchTemplate,
                  ExecutableFunction    executable,
                  MediaType[]           consumes = [],
                  MediaType[]           produces = [],
                  PreProcessor[]        preProcessors = [],
                  PostProcessor[]       postProcessors = [],
                  List<DefaultUriRoute> nestedRoutes = List:[])
            {
            this.httpMethod       = httpMethod;
            this.uriMatchTemplate = uriMatchTemplate;
            this.executable       = executable;
            this.consumes         = consumes;
            this.produces         = produces;
            this.nestedRoutes     = new Array(Mutable, nestedRoutes);
            this.preProcessors    = preProcessors.sorted();
            this.postProcessors   = postProcessors.sorted();
            }

        // ----- properties ------------------------------------------------------------------------

        /**
         * The HTTP method that this routes matches.
         */
        private HttpMethod httpMethod;

        /**
         * Child routes of this route.
         */
        private List<DefaultUriRoute> nestedRoutes;

        @Override
        PreProcessor[] preProcessors;

        @Override
        PostProcessor[] postProcessors;

        /**
         * The template to use to match this route to a URI.
         */
        @Override
        UriMatchTemplate uriMatchTemplate;

        /**
         * The function that handles executing the route.
         */
        @Override
        ExecutableFunction executable;

        @Override
        public/private MediaType[] consumes;

        @Override
        public/private MediaType[] produces;

        // ----- UriRoute implementation -----------------------------------------------------------

        @Override
        Boolean matchesMethod(HttpMethod method)
            {
            return this.httpMethod == method;
            }

        @Override
        conditional UriRouteMatch match(String uri)
            {
            if (UriMatchInfo info := uriMatchTemplate.match(uri))
                {
                return True, new DefaultUriRouteMatch(info, this);
                }
            return False;
            }

        // ----- Stringable methods ----------------------------------------------------------------

        @Override
        Int estimateStringLength()
            {
            return 0;
            }

        @Override
        Appender<Char> appendTo(Appender<Char> buf)
            {
            "DefaultUriRoute(".appendTo(buf);
            httpMethod        .appendTo(buf);
            ", "              .appendTo(buf);
            uriMatchTemplate  .appendTo(buf);
            ", "              .appendTo(buf);
            consumes          .appendTo(buf);
            ", "              .appendTo(buf);
            produces          .appendTo(buf);
            ")"               .appendTo(buf);

           return buf;
           }
        }
    }