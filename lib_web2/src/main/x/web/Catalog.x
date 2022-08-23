import routing.UriTemplate;

/**
 * The catalog of WebApp endpoints.
 */
const Catalog(WebServiceInfo[] services)
    {
    /**
     * The list of web service info.
     */
    WebServiceInfo[] services;

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

    typedef function WebService() as ServiceConstructor;

    static const WebServiceInfo<ServiceType extends WebService>(
                                Int                id,
                                String             path,
                                ServiceConstructor constructor,
                                EndpointInfo[]     endpoints,
                                EndpointInfo?      errorEndpoint,
                                EndpointInfo?      defaultEndpoint,
                                MethodInfo[]       interceptors,
                                MethodInfo[]       observers,
                                MethodInfo?        onError
                                )
        {
        Int endpointCount.get()
            {
            return endpoints.size;
            }
        }


    /**
     * The method info for a given service id.
     */
    static const MethodInfo(Method<WebService> method, Int sid)
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

    static const EndpointInfo
            extends MethodInfo
        {
        construct(Method<WebService> method, Int id, Int sid)
            {
            assert method.is(Endpoint);

            construct MethodInfo(method, sid);

            this.id        = id;
            this.template  = new UriTemplate(method.path);

            TODO
            }

        /**
         * The endpoint Method.
         */
        Method<WebService>+Endpoint endpoint.get()
            {
            return method.as(Endpoint);
            }

        /**
         * The endpoint id.
         */
        Int id;

        @Override
        HttpMethod httpMethod.get()
            {
            return endpoint.httpMethod;
            }

        /**
         * The URI template.
         */
        UriTemplate template;

        /**
         * The media type(s).
         */
        MediaType|MediaType[] mediaType;
        }
    }