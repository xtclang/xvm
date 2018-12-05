module TestAnonInner.xqiz.it
    {
    void run()
        {
        @Inject X.io.Console console;
        console.println("hello world! (anonymous inner class tests)");

        testSimple();
        }

    class Inner
        {
        construct(String s) {}
        }

    void testSimple()
        {
        @Inject X.io.Console console;
        console.println("\n** testSimple()");

        var o = new Object()
        // var o = new Inner("hello")
            {
            // @Inject X.io.Console console; // TODO test without this to force capture
            void run()
                {
                console.println("in run");
                }
            };

        o.run();

        console.println("done");
        }
    }
