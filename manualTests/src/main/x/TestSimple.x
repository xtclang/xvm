module TestSimple
    {
    @Inject Console console;

    package web import web.xtclang.org;

    import web.*;
    import ecstasy.reflect.*;

    void run( )
        {
        Method m = Test.test;

        Parameter p = m.params[0];
        console.println(&p.actualType); // used to show RTParameter

        Return r = m.returns[0];
        console.println(&r.actualType); // used to show RTReturn
        }

    @web.WebService("/")
    service Test
        {
        @web.Get
        String test(@QueryParam String test)
            {
            return "";
            }
        }
    }