@web.WebApp
module Hello
    {
    package web   import web.xtclang.org;
    package xenia import xenia.xtclang.org;

    @Inject Console console;

    void run()
        {
        assert:debug;
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

            @web.Get("e/{rest}")
            String echo(String rest)
                {
                assert:debug rest != "debug";
                return rest;
                }

            @web.Get("c")
            String count()
                {
                return $"count={simpleData.counter++}";
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
        }
    }