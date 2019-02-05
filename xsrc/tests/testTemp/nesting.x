module TestNesting.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("hello world! (nested class tests)");

        testSimple();
        testInsane();
        }

    void testSimple()
        {
        console.println("\n** testSimple()");
        new BOuter().bar();
        new DOuter().bar();
        new BOuter().new Inner().foo();
        }

    class BOuter // TODO unrelated compiler bug: "class BOuter extends Something" causes IllegalState
        {
        void bar()
            {
            new Inner().foo();
            }

        class Inner
            {
            void foo()
                {
                console.println("inner foo of B; this=" + this);
                }
            }
        }

    class DOuter
            extends BOuter
        {
        @Override
        class Inner
            {
            @Override
            void foo()
                {
                console.println("inner foo of D; this=" + this);
                }
            }
        }

    void testInsane()
        {
        console.println("\n** testInsane()");
        new PB().new C().foo();
        new DC().new P().new C().foo();
        }

    class PB                // P is for "parent" and B is for Base
        {
        class A             // A is for "abstract"
            {
            void foo()
                {
                console.println("PB.A.foo() this=" + this);
                }
            }

        interface I
            {
            void foo()
                {
                console.println("PB.I.foo() this=" + this);
                }
            }

        mixin M
            {
            void foo()
                {
                console.println("PB.M.foo() this=" + this);
                }
            }

        // TODO @M
        class C extends A implements I   // C is for "child"
            {
            @Override
            void foo()
                {
                console.println("PB.C.foo() this=" + this);
                super();
                }
            }
        }

    interface BI
        {
        class P extends PB
            {
            @Override class A
                {
                @Override
                void foo()
                    {
                    console.println("BI.P.A.foo() this=" + this);
                    }
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
                @Override
                void foo()
                    {
                    console.println("DC.P.A.foo() this=" + this);
                    super();
                    }
                }

            @Override class C // CANNOT SAY: extends BC.P.C
                {
                @Override
                void foo()
                    {
                    console.println("DC.P.C.foo() this=" + this);
                    super();
                    }
                }
            }
        }
    }
