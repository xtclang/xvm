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

    class PB                // P is for "parent" and B is for Base
        {
        Int size
            {
            Int get() {...}

            class A             // A is for "abstract"
                {
                void foo() {...}
                }

            interface I
                {
                void foo() {...}
                }

            mixin M
                {
                void foo() {...}
                }

            @M class C extends A implements I   // C is for "child"
                {
                void foo() {...}
                }
            }
        }

    interface BI
        {
        class P extends PB
            {
            @Override class A
                {
                void foo() {...}
                void bar() {...}
                }

            // implied class A
            //     {
            //     }

            // implied mixin M
            //     {
            //     }

            // implied interface I
            //     {
            //     }

            // implied class C extends PB.C extends BI.P.A extends PB.A
            //     {
            //     }

            // a call to C.foo() has the following potential call chain:
            // 1) BI.P.M.foo()
            // 2) PB.M.foo()
            // 1) BI.P.C.foo()
            // 2) PB.C.foo()
            // 3) BI.P.A.foo()
            // 4) PB.A.foo()
            // 5) Object.foo() (doesn't exist)
            // 6) <default> I.foo()
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
            @Override class A
                {
                // we need a name on all sub-classes
                String name;
                }

            @Override class C // CANNOT SAY: extends BC.P.C
                {
                }

            case XYZ extends Number {}
            }
        }
    }
