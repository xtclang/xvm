/**
 * The Web Server API.
 */
module web.xtclang.org
    {
    package collections import collections.xtclang.org;
    package json import json.xtclang.org;

    import ecstasy.reflect.Parameter;

// TODO temporary stuff to move or remove (See Server#Pre and Server#Post)
    interface PreProcessor
        {
        void process(Request request);
        }

    interface PostProcessor
        {
        void process(Request request, Response response);
        }
// end TODO

    /**
     * A mixin that represents a set of endpoints for a specific URI path.
     *
     * Example:
     *
     *     @web.WebModule
     *     module HelloWorld
     *         {
     *         package web import web.xtclang.org;
     *
     *         @web.WebService("/")
     *         service Hello
     *             {
     *             @web.Get("hello")
     *             @web.Produces("text/plain")
     *             String sayHello()
     *                 {
     *                 return "Hello World";
     *                 }
     *             }
     *         }
     */
    mixin WebService(String path = "/")
            into service;

    /**
     * This annotation, `@LoginRequired`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as requiring authentication.
     *
     * A method (any `Endpoint`, such as `@Get` or `@Post`) that handles a web service call will
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
            into service | Method;

    /**
     * This annotation, `@LoginOptional`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as **not** requiring
     * authentication.
     *
     * For more information, see the detailed description of the [@LoginRequired](LoginRequired)
     * annotation.
     */
    mixin LoginOptional
            into service | Method;

    /**
     * This annotation, `@HttpsRequired`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as requiring Transport Level
     * Security (TLS), sometimes also referred to using the obsolete term "SSL".
     *
     * A method (any `Endpoint`, such as `@Get` or `@Post`) that handles a web service call will
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
            into service | Method;

    /**
     * This annotation, `@HttpsOptional`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as **not** requiring Transport
     * Level Security (TLS).
     *
     * For more information, see the detailed description of the [@HttpsRequired](HttpsRequired)
     * annotation.
     */
    mixin HttpsOptional
            into service | Method;

    /**
     * This annotation, `@StreamingRequest`, is used to mark a web service call -- or any containing
     * class thereof, up to the level of the web module itself -- as **not** requiring the content
     * of the incoming HTTP request to be fully buffered. This allows the web server implementation
     * to save memory by streaming the content, instead of reading it entirely into memory before
     * (or while) processing the request.
     */
    mixin StreamingRequest
            into service | Method;

    /**
     * This annotation, `@StreamingResponse`, is used to mark a web service call -- or any
     * containing class thereof, up to the level of the web module itself -- as **not** requiring
     * the content of the outgoing HTTP response to be fully buffered. This allows the web server
     * implementation to save memory by streaming the content, instead of holding it entirely in
     * memory before starting to send it back to the client.
     */
    mixin StreamingResponse
            into service | Method;


    // ----- handler method annotations ------------------------------------------------------------

    /**
     * A generic HTTP endpoint.
     *
     * TODO example
     *
     * @param httpMethod     the name of the HTTP method
     * @param path       the optional path to reach this endpoint
     */
    mixin Endpoint(HttpMethod httpMethod, String path = "")
            into Method;

    /**
     * An HTTP `GET` method.
     *
     * @param path  the optional path to reach this endpoint
     */
    mixin Get(String path = "")
            extends Endpoint(GET, path)
            into Method;

    /**
     * An HTTP `POST` method.
     *
     * @param path  the optional path to reach this endpoint
     */
    mixin Post(String path = "")
            extends Endpoint(POST, path)
            into Method;

    /**
     * An HTTP `PATCH` method.
     *
     * @param path  the optional path to reach this endpoint
     */
    mixin Patch(String path = "")
            extends Endpoint(PATCH, path)
            into Method;

    /**
     * An HTTP `PUT` method.
     *
     * @param path  the optional path to reach this endpoint
     */
    mixin Put(String path = "")
            extends Endpoint(PUT, path)
            into Method;

    /**
     * An HTTP `DELETE` method.
     *
     * @param path  the optional path to reach this endpoint
     */
    mixin Delete(String path = "")
            extends Endpoint(DELETE, path)
            into Method;

    /**
     * A mixin to indicate the media-types produced by a particular component.
     *
     * The String `mediaType` value must **not** include wild-cards.
     *
     * TODO example
     */
    mixin Produces(String|MediaType|MediaType[] mediaType)
            into Method;

    /**
     * A mixin to indicate the media-types consumed by a particular component.
     *
     * The String `mediaType` value may include wild-cards, as allowed in an "Accept" header.
     *
     * TODO example
     */
    mixin Consumes(String|MediaType|MediaType[] mediaType)
            into Method;


    // ----- parameter annotations -----------------------------------------------------------------

    /**
     * A mixin to indicate that a Parameter is bound to a request value.
     */
    @Abstract mixin ParameterBinding(String templateParameter = "")
            into Parameter;

    /**
     * A mixin to indicate that a Parameter is bound to a request URI path segment.
     *
     * TODO example
     */
    mixin PathParam(String templateParameter = "")
            extends ParameterBinding(templateParameter);

    /**
     * A mixin to indicate that a Parameter is bound to a request URI query parameter.
     *
     * TODO example
     */
    mixin QueryParam(String templateParameter = "")
            extends ParameterBinding(templateParameter);

    /**
     * A mixin to indicate that a Parameter is bound to a request header value.
     *
     * TODO example
     */
    mixin HeaderParam(String templateParameter = "")
            extends ParameterBinding(templateParameter);

    /**
     * A mixin to indicate that a value is bound to a request or response body.
     *
     * TODO example
     */
    mixin BodyParam
            into Parameter;


    // ----- exceptions ----------------------------------------------------------------------------

    /**
     * An exception used to abort request processing and return a specific HTTP error status to the
     * client that issued the request.
     *
     * Generally, request processing should return a [Response] object; throwing a RequestAborted
     * exception is more expensive, but it allows request processing to immediately and abruptly
     * abort, without having to proceed through a method's (or a call stack's) normal termination.
     */
    const RequestAborted(HttpStatus status, String? text = Null, Exception? cause = Null)
            extends Exception(text, cause);
    }