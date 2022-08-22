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
                                Int                   id,
                                String                path,
                                ServiceConstructor    constructor,
                                EndpointInfo[]        endpoints,
                                EndpointInfo?         errorEndpoint,
                                EndpointInfo?         defaultEndpoint,
                                Method<ServiceType>[] interceptors,
                                Method<ServiceType>[] observers,
                                Method<ServiceType>?  onError
                                )
        {
        Int endpointCount.get()
            {
            return endpoints.size;
            }
        }

    static const EndpointInfo
        {
        construct(Method<WebService> method, Int id, Int sId)
            {
            assert method.is(Endpoint);

            this.endpoint  = method;
            this.id        = id;
            this.sid       = sid;
            this.template  = new UriTemplate(method.path);


            TODO
            }

        /**
         * The endpoint Method.
         */
        Method<WebService>+Endpoint endpoint;

        /**
         * The endpoint id.
         */
        Int id;

        /**
         * The endpoint service id.
         */
        Int sid;

        /**
         * The HTTP Method.
         */
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