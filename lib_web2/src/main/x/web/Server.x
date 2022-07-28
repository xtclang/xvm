/**
 * A representation of an HTTP server.
 */
interface Server
    {
    /**
     * A function that can process an incoming request is called a Handler.
     */
    typedef function Response(Request) as Handler;

    /**
     * A means to produce multiple Handlers, so that the server can concurrently execute handlers
     * for multiple incoming requests.
     */
    typedef function Handler() as HandlerFactory;

// REVIEW CP - service vs. endpoint factory
    /**
     * Register the specified `HandlerFactory` for the specified URI template.
     *
     * @param route    the URI template that indicates the requests that can be handled
     * @param factory  the means to produce handlers that can respond to the specified URI
     */
    void addRoute(/* TODO UriTemplate */ String route, HandlerFactory factory);

    /**
     * Register the specified `HandlerFactory` for all unhandled URIs.
     *
     * @param factory  the means to produce handlers that can respond to any URI not handled by a
     *                 route registered via [addRoute]
     */
    void routeDefault(HandlerFactory factory);

    void routeErrors(HandlerFactory factory);

// Watcher? Logger?
    /**
     * A function that is called with each incoming request is called an `Observer`. Despite the name,
     * the `Observer` operations are not guaranteed to be run before the [Handler] and/or any [Post]s
     * for the request.
     *
     * The `Observer` capability is useful for implementing simple capabilities, such as logging each
     * request, but the functionality is limited, and the `Observer` cannot change the Request or alter
     * the request processing control flow. For purposes of request processing, exceptions from the
     * `Observer` are ignored, including [RequestAborted].
     */
    typedef function void(Request) as Observer;

    /**
     * A means to produce multiple [Observer] handlers, to allow the server to increase concurrent
     * request handling execution.
     */
    typedef function Observer() as ObserverFactory;

    /**
     * Instruct the server to perform some operation on each incoming request. The order in which
     * [Observer]s are added is not significant, and the `Observer` operations are not guaranteed to run in
     * any specific order. Furthermore, and despite the name, the `Observer` operations are not even
     * guaranteed to be run before the [Handler] and any [Post]s for the request.
     *
     * @param factory  the means to produce [Observer] handlers that will perform the operation
     */
    void addObserver(ObserverFactory factory);

    /**
     * A function that is given the request to both pre- **and** post-process is called a `Post`.
     */
    typedef function Response(Handler, Request) as Post;

    /**
     * A means to produce multiple [Post] handlers, to allow the server to increase concurrent
     * request handling execution.
     */
    typedef function Post() as PostFactory;

    /**
     * Instruct the server to delegate each incoming request to the specified operation. The order
     * in which [Post]s are added to the server is significant: The most recently added `Post` will
     * be executed first for subsequent incoming requests.
     *
     * Each implementation of TODO
     *
     * @param factory  the means to produce [Post] handlers that will perform the operation
     */
    void addPost(PostFactory factory);
    }
