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

    class PB
        {
        class C
            {
            }
        }

    interface BI
        {
        class P extends PB
            {
            // implied class C
            //     {
            //     }
            }
        }
    interface DI1 extends BI
        {
        // implied class P
        //    {
        //    implied class C
        //        {
        //        }
        //    }
        }

    interface DI2 extends BI
        {
        @Override class P
            {
            // implied class C
            //     {
            //     }
            }
        }

    interface DI3 extends BI
        {
        @Override class P
            {
            @Override class C
                {
                }
            }
        }

    class BC /* implied extends Object */ implements DI1
        {
        // implied class P
        //    {
        //    implied class C
        //        {
        //        }
        //    }
        }

    class DC extends BC
        {
        @Override class P
            {
            @Override class C
                {
                }
            }
        }
    }
