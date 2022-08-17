/**
 * The Web Server implementation.
 */
module xenia.xtclang.org
    {
    package aggregate import aggregate.xtclang.org;

    package net import net.xtclang.org;
    package web import web.xtclang.org;

    import web.HttpServer;
    import web.Server;
    import web.WebApp;

    Server createServer(String address, WebApp app)
        {
        @Inject(opts=address) HttpServer server;

        return TODO
        }
    }