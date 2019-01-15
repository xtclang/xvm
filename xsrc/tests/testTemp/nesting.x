module TestNesting.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("hello world! (nested class tests)");

        testSimple();
        }

    // TODO "class BOuter extends Something" causes IllegalState
    class BOuter
        {
        void bar()
            {
            new Inner().foo();
            }

        class Inner
            {
            void foo()
                {
                console.println("inner foo on " + this);
                }
            }
        }

    class DOuter
            extends BOuter
        {
        @Override
        class Inner
            {
//            @Override
//            void foo()
//                {
//                console.println("inner foo of D");
//                }
            }
        }

    void testSimple()
        {
        console.println("\n** testSimple()");
        new BOuter().bar();
        new DOuter().bar();
        // new BOuter().new Inner().foo();
        }
    }
