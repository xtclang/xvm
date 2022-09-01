import routing.UriTemplate;

/**
 * The catalog of WebApp endpoints.
 */
const Catalog(WebApp webApp, WebServiceInfo[] services, Class[] sessionMixins)
    {
    /**
     * The list of [WebServiceInfo] objects describing the [WebService] classes discovered within
     * the application.
     */
    WebServiceInfo[] services;

    /**
     * The list of [Session] mixin classes discovered within the application.
     */
    Class[] sessionMixins;

    /**
     * The total number of WebServices.
     */
    Int serviceCount.get()
        {
        return services.size;
        }

    /**
     * The total number of endpoints.
     */
    @Lazy Int endpointCount.calc()
        {
        return services.map(WebServiceInfo.endpointCount).reduce(new aggregate.Sum<Int>());
        }

    /**
     * Find the most specific "onError" MethodInfo for the specified service.
     */
    MethodInfo? findOnError(Int wsid)
        {
        WebServiceInfo[] services = this.services;
        String           path     = services[wsid].path;

        // the most specific route has the priority
        for (Int id : wsid..0)
            {
            WebServiceInfo serviceInto = services[id];
            if (path.startsWith(serviceInto.path), MethodInfo onError ?= serviceInto.onError)
                {
                return onError;
                }
            }
        return Null;
        }

    typedef function WebService() as ServiceConstructor;

    static const WebServiceInfo<ServiceType extends WebService>(
                                Int                id,
                                String             path,
                                ServiceConstructor constructor,
                                EndpointInfo[]     endpoints,
                                EndpointInfo?      defaultEndpoint,
                                MethodInfo[]       interceptors,
                                MethodInfo[]       observers,
                                MethodInfo?        onError,
                                MethodInfo?        route
                                )
        {
        Int endpointCount.get()
            {
            return endpoints.size;
            }
        }

    /**
     * The method info for a given WebService id.
     */
    static const MethodInfo(Method<WebService> method, Int wsid)
        {
        /**
         * The HTTP Method.
         */
        HttpMethod? httpMethod.get()
            {
            return method.is(Observe|Intercept)
                    ? method.httpMethod
                    : Null;
            }

        }

    /**
     * The endpoint info for a given WebService id.
     */
    static const EndpointInfo
            extends MethodInfo
        {
        construct(Method<WebService> method, Int id, Int wsid)
            {
            assert method.is(Endpoint);

            this.id = id;
            construct MethodInfo(method, wsid);

            template = new UriTemplate(method.path);
            produces = method.is(Produces)
                        ? method.produces
                        : [];
            consumes = method.is(Consumes)
                        ? method.consumes
                        : [];
            TODO
            }

        @Override
        HttpMethod httpMethod.get()
            {
            return endpoint.httpMethod;
            }

        /**
         * The endpoint Method.
         */
        Endpoint endpoint.get()
            {
            return method.as(Endpoint);
            }

        /**
         * The endpoint id.
         */
        Int id;

        /**
         * The URI template.
         */
        UriTemplate template;

        /**
         * The media type(s) this endpoint consumes.
         */
        MediaType|MediaType[] consumes;

        /**
         * The media type(s) this endpoint produces.
         */
        MediaType|MediaType[] produces;

        /**
         * Indicates if the endpoint return value is a _conditional return_.
         */
        Boolean conditionalResult.get()
            {
            return method.conditionalResult;
            }

        MediaType resolveResponseContentType(AcceptList accepts)
            {
            TODO
            }
        }
    }