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
                }
            };

        o.run();
        }
    }