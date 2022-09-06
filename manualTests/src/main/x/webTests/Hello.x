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

            @web.Get("h")
            String hello()
                {
                return "hello";
                }

            @web.Get("g")
            String goodbye()
                {
                return $"goodbye {simpleData.counter++}";
                }

            @web.Default @web.Get
            String wtf()
                {
                assert:debug;
                return "what?";
                }

            static mixin SimpleData
                    into web.Session
                {
                Int counter;
                }
            }
        }
    }