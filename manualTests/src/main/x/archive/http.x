/**
 * An end to end test for the http.Server and http.Client.
 */
module TestHttp
    {
    @Inject Console console;

    import ecstasy.http.Client;
    import ecstasy.http.Response;
    import ecstasy.http.Server;

    void run()
        {
        console.print("Starting test");

        Handler handler = new Handler();
        Server  server  = new Server(8080, handler);
        server.start();

        String uri = $"http://localhost:{server.port}";

        console.print($"Web server is running {uri}");

        console.print("Creating HTTP clients");
        HttpClient client1 = new HttpClient();
        HttpClient client2 = new HttpClient();

        console.print("Executing request 1");
        Int status1 = client1.send^(uri);
        console.print("Executing request 2");
        Int status2 = client2.send^(uri);

        console.print("Asserting responses");
        assert:test status1 == 200;
        assert:test status2 == 200;

        server.stop();
        console.print("Test complete");
        }

    @Concurrent service HttpClient
        {
        construct ()
            {
            client = new Client(0, Normal, 0);
            }

        private Client client;

        Int send(String uri)
            {
            @Inject Console console;
            console.print($"Sending request to {uri}");
            (Int status, String[] headerNames, String[][] headerValues, Byte[] body) = client.send(uri);

            assert:test status == 200;
            console.print($"Received response {status}");
            console.print($"Received response {headerNames}");
            console.print($"Received response {headerValues}");
            console.print($"Received response {body}");
            return status;
            }
        }

    /**
     * The Server.Handler implementation to handle all the requests
     * to the web server.
     */
    @Concurrent service Handler
            implements Server.Handler
        {
        @Override
        void handle(String uri, String method, String[] headerNames, String[][] headerValues,
                    Byte[] body, Response response)
            {
            RequestHandler requestHandler = new RequestHandler();
            requestHandler.handle^(uri, method, headerNames, headerValues, body, response);
            }
        }

    /**
     * The Server.Handler implementation to handle all the requests
     * to the web server.
     */
    service RequestHandler
        {
        void handle(String uri, String method, String[] headerNames,
                    String[][] headerValues, Byte[] body, Response response)
            {
            @Inject Console console;
            console.print($"Entered RequestHandler uri={uri} method={method}");

            String[]   respHeaderNames  = ["Foo", "Bar"];
            String[][] respHeaderValues = [["One"], ["Two", "Three"]];
            Int        status           = 200;
            console.print($"Leaving RequestHandler status={status}");
            response.send^(status, respHeaderNames, respHeaderValues, [0, 1, 2, 3]);
            }
        }
    }