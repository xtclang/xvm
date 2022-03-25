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
     * A means to produce multiple Handlers.
     */
    typedef function Handler() as HandlerFactory;

    /**
     * Register the specified `HandlerFactory` for the specified URI template.
     *
     * @param route    the URI template that indicates the requests that can be handled
     * @param factory  the means to produce handlers that can respond to the specified URI
     */
    void addRoute(UriTemplate route, HandlerFactory factory);

    /**
     * Register the specified `HandlerFactory` for all unhandled URIs.
     *
     * @param factory  the means to produce handlers that can respond to any URI not handled by a
     *                 route registered via [addRoute]
     */
    void defaultRoute(HandlerFactory factory);

//    /**
//     * A function that is given the request to pre-process is called a `Pre`.
//     */
//    typedef function void(Request) as Pre;
//
//    /**
//     * A means to produce multiple Pre handlers.
//     */
//    typedef function Pre() as PreFactory;
//
//    /**
//     * A function that is given the request to pre-process is called a `Post`.
//     */
//    typedef function (Request, Response)(Request, Response) as Post;
//
//    /**
//     * A means to produce multiple Post handlers.
//     */
//    typedef function Post() as PostFactory;
//
//    /**
//     * Register the specified `HandlerFactory` for the specified URI template.
//     *
//     * @param route    the URI template that indicates the requests that can be handled
//     * @param factory  the means to produce handlers that can respond to the specified URI
//     */
//    void addPre(PreFactory factory); // TODO order?
//
//    /**
//     * Register the specified `PostFactory` for the specified URI template.
//     *
//     * @param route    the URI template that indicates the requests that can be handled
//     * @param factory  the means to produce handlers that can respond to the specified URI
//     */
//    void addPost(PostFactory factory); // TODO order
    }
