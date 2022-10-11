@web.WebApp
module Hello
    {
    package web   import web.xtclang.org;
    package xenia import xenia.xtclang.org;

    import web.Default;
    import web.Get;
    import web.Produces;
    import web.Session;
    import web.StaticContent;
    import web.WebService;

    void run()
        {
        @Inject Console console;

        xenia.createServer("localhost:8080", this);

        console.println(\|Use the curl command to test, for example:
                         |
                         |  curl -i -w '\n' -X GET http://localhost:8080/h
                         |
                         |Use Ctrl-C to stop.
                        );
        }

    package inner
        {
        @WebService("/")
        service Simple
            {
            SimpleData simpleData.get()
                {
                return session?.as(SimpleData) : assert;
                }

            @Get
            String hello()
                {
                return "hello";
                }

            @Get("c")
            Int count(SimpleData sessionData)
                {
                return sessionData.counter++;
                }

            @Default @Get
            @Produces(Text)
            String askWhat()
                {
                return "what?";
                }

            static mixin SimpleData
                    into Session
                {
                Int counter;
                }
            }

        @WebService("/e")
        service Echo
            {
            @Get("{path}")
            String[] echo(String path)
                {
                assert:debug path != "debug";

                Map<String, String|List<String>> query = request?.queryParams : assert;
                return [path, query.empty ? "" : $"{query}"];
                }
            }

        @StaticContent("/static", /resources/hello)
        service Content
            {
            }
        }
    }