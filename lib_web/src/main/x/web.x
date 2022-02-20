/**
 * The Web Server API.
 */
module web.xtclang.org
    {
    package collections import collections.xtclang.org;
    package json import json.xtclang.org;

    import ecstasy.reflect.Parameter;

    /**
     * The `@HttpModule` annotation is used to mark a module as containing discoverable HTTP
     * endpoints.
     *
     * Within an `@HttpModule`-annotated module, a number of `@Inject` injections are assumed to be
     * supported by the container:
     *
     * |    Type      |    Name    | Description                        |
     * |--------------|------------|------------------------------------|
     * | HttpRequest  |   _(any)_  | The current request object         |
     * | HttpResponse |   _(any)_  | The current response object        |
     */
    mixin HttpModule
            into Module;

    /**
     * This annotation, `@LoginRequired`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as requiring authentication.
     *
     * A method (any `HttpEndpoint`, such as `@Get` or `@Post`) that handles a web service call will
     * require authentication iff:
     *
     * * the web service method is annotated with `LoginRequired`, or
     * * the web service method is not annotated with `LoginOptional`, and the parent class requires
     *   authentication.
     *
     * A parent class will require authentication iff:
     *
     * * the class is annotated with `LoginRequired`, or
     * * the class is not annotated with `LoginOptional`, and there is a containing class, and the
     *   containing class requires authentication, or
     * * the class is a module (and thus has no containing class), and the host for the module has
     *   been configured to require authentication by default.
     *
     * The purpose of this design is to allow the use of annotations for specifying the requirement
     * for authentication, but only requiring those annotations within the class hierarchy at the
     * few points where a change occurs from "requiring authentication" to "not requiring
     * authentication", or vice versa.
     */
    mixin LoginRequired
            into Class | Method;

    /**
     * This annotation, `@LoginOptional`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as **not** requiring
     * authentication.
     *
     * For more information, see the detailed description of the [@LoginRequired](LoginRequired)
     * annotation.
     */
    mixin LoginOptional
            into Class | Method;

    /**
     * This annotation, `@HttpsRequired`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as requiring Transport Level
     * Security (TLS), sometimes also referred to using the obsolete term "SSL".
     *
     * A method (any `HttpEndpoint`, such as `@Get` or `@Post`) that handles a web service call will
     * require TLS iff:
     *
     * * the web service method is annotated with `HttpsRequired`, or
     * * the web service method is not annotated with `HttpsOptional`, and the parent class requires
     *   TLS.
     *
     * A parent class will require TLS iff:
     *
     * * the class is annotated with `HttpsRequired`, or
     * * the class is not annotated with `HttpsOptional`, and there is a containing class, and the
     *   containing class requires TLS, or
     * * the class is a module (and thus has no containing class), and the host for the module has
     *   been configured to require TLS by default.
     *
     * The purpose of this design is to allow the use of annotations for specifying the requirement
     * for TLS, but only requiring those annotations within the class hierarchy at the
     * few points where a change occurs from "requiring TLS" to "not requiring
     * TLS", or vice versa.
     */
    mixin HttpsRequired
            into Class | Method;

    /**
     * This annotation, `@HttpsOptional`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as **not** requiring Transport
     * Level Security (TLS).
     *
     * For more information, see the detailed description of the [@HttpsRequired](HttpsRequired)
     * annotation.
     */
    mixin HttpsOptional
            into Class | Method;

    /**
     * Each `HttpHeader` that appears in an [HttpMessage] is simply a `String` name and a `String`
     * value.
     */
    typedef Tuple<String, String> as HttpHeader_W2;

    /**
     * Each [HttpMessage] contains zero or more `HttpHeader` objects.
     */
    typedef List<HttpHeader_W2> as HttpHeaders_W2;

    /**
     * A representation of a protocol used for web services.
     */
    const Protocol(String name, Version version, Boolean TLS, Int family, String fullText)
        {
        /**
         * The name of the protocol, which is _normally_ either "HTTP" or "HTTPS".
         */
        String name;

        /**
         * The version of the protocol, such as HTTP version `1.1` or `2`.
         */
        Version version;

        /**
         * True iff the protocol provides "transport layer security", which is the case for HTTPS.
         */
        Boolean TLS;

        /**
         * An archaic number representing the protocol for people old enough to remember Y2K.
         */
        Int family;

        /**
         * The full text of the protocol or scheme portion of the [HttpMessage], such as "HTTP/2".
         */
        String fullText;
        }

    /**
     * A set of the the most common protocols in use today. These are pre-defined only as a means to
     * avoid having to instantiate additional copies of the same common information.
     */
    enum Protocols(String name, Version version, Boolean TLS, Int family, String fullText)
            extends Protocol(name, version, TLS, family, fullText)
        {
        HTTP1  ("HTTP"  , v:1  , False, 2 , "HTTP/1.0"),
        HTTP11 ("HTTP"  , v:1.1, False, 2 , "HTTP/1.1"),
        HTTPS11("HTTPS" , v:1.1, True , 23, "HTTP/1.1"),
        HTTP2  ("HTTP"  , v:2  , False, 2 , "HTTP/2"  ),
        HTTPS2 ("HTTPS" , v:2  , True , 23, "HTTP/2"  ),
        HTTP3  ("HTTP"  , v:3  , False, 2 , "HTTP/3"  ),
        HTTPS3 ("HTTPS" , v:3  , True , 23, "HTTP/3"  ),
        }

    /**
     * TODO
     */
    interface Body_W2
        {
        /**
         * TODO
         */
        Byte[] toBytes();
        String toText();
        }

    /**
     * TODO
     */
    interface HttpMessage_W2
        {
        /**
         * TODO
         */
        @RO HttpHeaders_W2 headers;

        /**
         * TODO
         */
        Byte[] body;
        }

    /**
     * TODO
     */
    interface HttpRequest_W2
            extends HttpMessage_W2
        {
//        URI

//        query

        @RO HttpMethod httpMethod;

        @RO Protocol protocol;

        @RO String authority;

        @RO String path;

        }

    /**
     * TODO
     */
    interface HttpResponse_W2
            extends HttpMessage_W2
        {
        }

    /**
     * TODO
     */
    typedef function void(HttpRequest, HttpResponse) as HttpHandler_W2;

    /**
     * A handler for an HTTP request response pair, that will typically be mapped to a Route.
     * TODO not used
     */
    interface RequestHandler
        {
        void handleRequest(HttpRequest req, HttpResponse resp);
        }

    /**
     * A mixin that represents a set of endpoints for a specific URI path.
     * TODO not used
     */
    mixin WebService(String path = "/")
            into Class;

    /**
     * A generic HTTP endpoint.
     *
     * @param httpMethod     the name of the HTTP method
     * @param path       the optional path to reach this endpoint
     */
    mixin HttpEndpoint(HttpMethod httpMethod, String path = "")
            into Method;

    /**
     * An HTTP `GET` method.
     *
     * @param path  the optional path to reach this endpoint
     */
    mixin Get(String path = "")
            extends HttpEndpoint(GET, path)
            into Method;

    /**
     * An HTTP `POST` method.
     *
     * @param path  the optional path to reach this endpoint
     */
    mixin Post(String path = "")
            extends HttpEndpoint(POST, path)
            into Method;

    /**
     * An HTTP `PATCH` method.
     *
     * @param path  the optional path to reach this endpoint
     */
    mixin Patch(String path = "")
            extends HttpEndpoint(PATCH, path)
            into Method;

    /**
     * An HTTP `PUT` method.
     *
     * @param path  the optional path to reach this endpoint
     */
    mixin Put(String path = "")
            extends HttpEndpoint(PUT, path)
            into Method;

    /**
     * An HTTP `DELETE` method.
     *
     * @param path  the optional path to reach this endpoint
     */
    mixin Delete(String path = "")
            extends HttpEndpoint(DELETE, path)
            into Method;

    /**
     * A mixin to indicate the media-types produced by a particular component.
     */
    mixin Produces(String mediaType = "*/*")
            into Method;

    /**
     * A mixin to indicate the media-types consumed by a particular component.
     * TODO multi?
     */
    mixin Consumes(String mediaType = "*/*")
            into Method;

    /**
     * A mixin to indicate the media-types accepted by a particular component.
     * TODO not used
     */
    mixin Accepts(String mediaType = "*/*")
            into Method;

    /**
     * A mixin to indicate that a Parameter is bound to a request value.
     */
    mixin ParameterBinding(String templateParameter = "")
            into Parameter;

    /**
     * A mixin to indicate that a Parameter is bound to a request URI path segment.
     */
    mixin PathParam(String templateParameter = "")
            extends ParameterBinding(templateParameter);

    /**
     * A mixin to indicate that a Parameter is bound to a request URI query parameter.
     */
    mixin QueryParam(String templateParameter = "")
            extends ParameterBinding(templateParameter);

    /**
     * A mixin to indicate that a Parameter is bound to a request header value.
     */
    mixin HeaderParam(String templateParameter = "")
            extends ParameterBinding(templateParameter);

    /**
     * A mixin to indicate that a value is bound to a request or response body.
     */
    mixin Body
            into Parameter;

    interface Prioritized
            extends Orderable
        {
        static immutable Int Normal = 1000;

        @RO Int priority = Normal;

        // ----- Orderable -----------------------------------------------------------------------------

        /**
         * Prioritized are equal if their priorities are equal.
         */
        static <CompileType extends Prioritized> Boolean equals(CompileType value1, CompileType value2)
            {
            return value1.priority == value2.priority;
            }

        /**
         * Prioritized are compared by their priority.
         */
        static <CompileType extends Prioritized> Ordered compare(CompileType value1, CompileType value2)
            {
            return value1.priority <=> value2.priority;
            }
        }

    interface PreProcessor
            extends Prioritized
        {
        void process(HttpRequest request);
        }

    interface PostProcessor
            extends Prioritized
        {
        void process(HttpRequest request, HttpResponse response);
        }

    /**
     * A provider of a function, typically a handler for an HTTP request.
     */
    interface ExecutableFunction
            extends Const
        {
        function void () createFunction();

        @RO Boolean conditionalResult;
        }

    /**
     * Indicates an HTTP error with a specific status code.
     */
    const HttpException(HttpStatus status, String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);

    /**
     * Indicates that a web resource does not exist.
     */
    const NotFound(String? text = Null, Exception? cause = Null)
            extends HttpException(HttpStatus.NotFound, text, cause);
    }