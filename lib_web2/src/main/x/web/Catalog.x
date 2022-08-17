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

    typedef function WebService() as ServiceConstructor;

    static const WebServiceInfo(Type<WebService>   serviceType,
                                Int                id,
                                String             path,
                                ServiceConstructor constructor,
                                EndpointInfo[]     endpoints,
                                EndpointInfo?      errorEndpoint,
                                EndpointInfo?      defaultEndpoint
                                );

    static const EndpointInfo
        {
        construct(Method<WebService> endpoint, Int id, Int wsId)
            {
            TODO
            }

        /**
         * The endpoint Method.
         */
        Method<WebService> endpoint;

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
        HttpMethod httpMethod;

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