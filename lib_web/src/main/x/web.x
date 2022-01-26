/**
 * The Web Server API.
 */
module web.xtclang.org
    {
    package collections import collections.xtclang.org;
    package json import json.xtclang.org;

    import ecstasy.reflect.Parameter;

    /**
     * A handler for an HTTP request response pair, that will typically be mapped to a Route.
     */
    interface RequestHandler
        {
        void handleRequest(HttpRequest req, HttpResponse resp);
        }

    /**
     * A mixin that represents a set of endpoints for a specific URI path.
     */
    mixin WebService(String path = "/")
            into Class
        {
        }

    /**
     * A generic HTTP endpoint.
     *
     * @param methodName the name of the HTTP method
     * @param method     the name of the HTTP method
     * @param path       the optional path to reach this endpoint
     */
    mixin HttpEndpoint(HttpMethod method, String path = "")
            into Method
        {
        }

    /**
     * An HTTP `GET` method.
     *
     * @param path  the optional path to reach this endpoint
     */
    mixin Get(String path = "")
            extends HttpEndpoint(HttpMethod.GET, path)
            into Method
        {
        }

    /**
     * An HTTP `POST` method.
     *
     * @param path  the optional path to reach this endpoint
     */
    mixin Post(String path = "")
            extends HttpEndpoint(HttpMethod.POST, path)
            into Method
        {
        }

    /**
     * An HTTP `PATCH` method.
     *
     * @param path  the optional path to reach this endpoint
     */
    mixin Patch(String path = "")
            extends HttpEndpoint(HttpMethod.PATCH, path)
            into Method
        {
        }

    /**
     * An HTTP `PUT` method.
     *
     * @param path  the optional path to reach this endpoint
     */
    mixin Put(String path = "")
            extends HttpEndpoint(HttpMethod.PUT, path)
            into Method
        {
        }

    /**
     * An HTTP `DELETE` method.
     *
     * @param path  the optional path to reach this endpoint
     */
    mixin Delete(String path = "")
            extends HttpEndpoint(HttpMethod.DELETE, path)
            into Method
        {
        }

    /**
     * A mixin to indicate the media-types produced by a particular component.
     */
    mixin Produces(String mediaType = "*/*")
            into Method
        {
        }

    /**
     * A mixin to indicate the media-types consumed by a particular component.
     */
    mixin Consumes(String mediaType = "*/*")
            into Method
        {
        }

    /**
     * A mixin to indicate the media-types accepted by a particular component.
     */
    mixin Accepts(String mediaType = "*/*")
            into Method
        {
        }

    /**
     * A mixin to indicate that a Parameter is bound to a request value.
     */
    mixin ParameterBinding(String templateParameter = "")
            into Parameter
        {
        }

    /**
     * A mixin to indicate that a Parameter is bound to a request URI path segment.
     */
    mixin PathParam(String templateParameter = "")
            extends ParameterBinding(templateParameter)
        {
        }

    /**
     * A mixin to indicate that a Parameter is bound to a request URI query parameter.
     */
    mixin QueryParam(String templateParameter = "")
            extends ParameterBinding(templateParameter)
        {
        }

    /**
     * A mixin to indicate that a Parameter is bound to a request header value.
     */
    mixin HeaderParam(String templateParameter = "")
            extends ParameterBinding(templateParameter)
        {
        }


    /**
     * A mixin to indicate that a value is bound to a request or response body.
     */
    mixin Body
        into Parameter
        {
        }

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