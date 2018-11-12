module TestAnonInner.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("hello world! (anonymous inner class tests)");

        testSimple();
        }

    void testSimple()
        {
        console.println("\n** testSimple()");

        var o = new Object()
            {
            void run()
                {
//                @Inject X.io.Console console2;
//                console2.println("in run");
                }
            };

        o.run();

        console.println("done");
        }
    }