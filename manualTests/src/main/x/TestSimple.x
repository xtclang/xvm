module TestSimple
    {
    @Inject Console console;

    package web import web.xtclang.org;

    import web.*;

    void run( )
        {
        Method m = Test.test;
        if (m.is(Get))
            {
            console.println(m.template);
            }
        if (m.is(Produces))
            {
            console.println(m.produces);
            }
        }

    @web.WebService("/")
    service Test
        {
        @web.Get("test")  // this used to fail to compile
        @web.Produces(Json)
        @StreamingResponse
        String test()
            {
            return "";
            }
        }
    }