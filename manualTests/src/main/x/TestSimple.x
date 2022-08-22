module TestSimple
    {
    @Inject Console console;

    void run()
        {
        @Inject("console") MyConsole myc;

        console.println(myc);
        myc.println(console);

        @Inject("server", "localhost:8080") MyHttpServer httpServer;
        console.println(httpServer);
        httpServer.close();
        }

    interface MyConsole
        {
        void print(Object o);
        void println(Object o = "");
        String readLine();
        Boolean echo(Boolean flag);
        }

    interface MyHttpServer
            extends Closeable
        {
        void attachHandler(Handler handler);

        void send(Object context, Int status, String[] headerNames, String[][] headerValues, Byte[] body);

        static interface Handler
            {
            void handle(Object context, String uri, String method,
                        String[] headerNames, String[][] headerValues, Byte[] body);
            }
        }
    }