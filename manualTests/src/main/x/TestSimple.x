module TestSimple
    {
    @Inject Console console;

    package web import web.xtclang.org;

    import web.*;

    void run()
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

    // @web.WebService("/") // without this annotation it used to compile and assert at run-time
    service Test
        {
        @web.Produces(Json)
        @web.Get("test")
        String test()
            {
            return "";
            }
        }
    }