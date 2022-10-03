@web.WebApp
module Hello
    {
    package web   import web.xtclang.org;
    package xenia import xenia.xtclang.org;

    @Inject Console console;

    void run()
        {
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
        @web.WebService("/")
        service Simple
            {
            SimpleData simpleData.get()
                {
                return session?.as(SimpleData) : assert;
                }

            @web.Get
            String hello()
                {
                return "hello";
                }

            @web.Get("c")
            String count(SimpleData sessionData)
                {
                return $"count={sessionData.counter++}";
                }

            @web.Default @web.Get
            String askWhat()
                {
                return "what?";
                }

            static mixin SimpleData
                    into web.Session
                {
                Int counter;
                }
            }

        @web.WebService("/e")
        service Echo
            {
            @web.Get("{path}")
            String echo(String path)
                {
                assert:debug path != "debug";

                Map<String, String|List<String>> query = request?.queryParams : assert;
                return path + (query.empty ? "" : $"?{query}");
                }
            }
        }
    }