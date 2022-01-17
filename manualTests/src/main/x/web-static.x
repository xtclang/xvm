/**
 * This test will run a web server on localhost port 8080
 * that serves static content from files.
 */
module TestWebAppStaticContent
    {
    package web import web.xtclang.org;

    import ecstasy.fs.Directory;
    import ecstasy.fs.File;
    import ecstasy.http.Client;

    import web.HttpStatus;
    import web.MediaType;
    import web.StaticContent;
    import web.WebServer;


    void run()
        {
        @Inject Console console;
        @Inject Directory curDir;

        console.println($"Testing Web App Static Content: curDir={curDir}");

        Directory        rootDir  =  curDir.dirFor("src")
                                           .dirFor("main")
                                           .dirFor("x")
                                           .dirFor("sock-shop")
                                           .dirFor("catalog");

        assert Directory imageDir := rootDir.findDir("images");

        // Create the web server, add the endpoints, and start.
        WebServer server = new WebServer(8080)
                .addRoutes(new StaticContent(imageDir, MediaType.IMAGE_JPEG_TYPE), "/catalogue/images")
                .start();

        String uriBase = $"http://localhost:{server.port}";

        console.println($"Started WebServer {uriBase}");

        // this will effectively wait for the specified duration...
        wait^(server, Duration:60s);
        }

    @Concurrent service HttpClient
        {
        construct ()
            {
            client = new Client(0, Normal, 0);
            }

        private Client client;

        (Int, String[], String[][], Byte[]) send(String uri)
            {
            return client.send(uri);
            }
        }

    void wait(WebServer server, Duration duration)
        {
        @Inject Timer timer;

        @Future Tuple<> result;

        // schedule a "forced shutdown"
        timer.schedule(duration, () ->
            {
            if (!&result.assigned)
                {
                @Inject Console console;
                console.println("Shutting down the test");
                server.stop();
                result=Tuple:();
                }
            });

        private void checkRunning(WebServer server, Timer timer, FutureVar<Tuple> result)
            {
            if (server.isRunning())
                {
                timer.schedule(Duration.ofSeconds(10), &checkRunning(server, timer, result));
                return;
                }

            if (!result.assigned)
                {
                @Inject Console console;
                console.println("The web server has stopped");
                result.set(Tuple:());
                }
            }

        // schedule a periodic check
        timer.schedule(Duration.ofSeconds(10), &checkRunning(server, timer, &result));
        return result;
        }
    }
