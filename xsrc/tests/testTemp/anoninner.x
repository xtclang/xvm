module TestAnonInner.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("hello world! (anonymous inner class tests)");

        testSimple();
        }

    class Inner
        {
        construct(String s) {}
        }

    void testSimple()
        {
        console.println("\n** testSimple()");

        var o = new Inner("hello")
            {
            @Inject X.io.Console console; // TODO test without this to force capture
            void run()
                {
                console.println("in run");
                }
            };

        o.run();

        console.println("done");
        }
    }
