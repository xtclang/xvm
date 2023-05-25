module TestNesting
    {
    @Inject ecstasy.io.Console console;

    void run()
        {
        testSimple();
        testInsane();
        }

    void testSimple()
        {
        console.print("\n** testSimple()");
        new BOuter().bar();
        new DOuter().bar();
        new BOuter().new InnerC().foo();
        }

    class BOuter
        {
        void bar()
            {
            new InnerC().foo();
            }

        class InnerC
            {
            void foo()
                {
                console.print("inner foo of B; this=" + this);
                }
            }
        }

    class DOuter
            extends BOuter
        {
        @Override
        class InnerC
            {
            @Override
            void foo()
                {
                console.print("inner foo of D; this=" + this);
                }
            }
        }

    void testInsane()
        {
        console.print("\n** testInsane()");
        new PB().new C().foo();
        new DC().new P().new C().foo();
        }

    class PB                // P is for "parent" and B is for Base
        {
        class A             // A is for "abstract"
            {
            void foo()
                {
                console.print("PB.A.foo() this=" + this);
                }
            }

        interface I
            {
            void foo()
                {
                console.print("PB.I.foo() this=" + this);
                }
            }

        mixin M into A
            {
            @Override
            void foo()
                {
                console.print("PB.M.foo() this=" + this);
                super();
                }
            }

        @M
        class C extends A implements I   // C is for "child"
            {
            @Override
            void foo()
                {
                console.print("PB.C.foo() this=" + this);
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
                    console.print("BI.P.A.foo() this=" + this);
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
            // 3) BI.P.C.foo()
            // 4) PB.C.foo()
            // 5) BI.P.A.foo()
            // 6) PB.A.foo()
            // 7) Object.foo() (doesn't exist)
            // 8) <default> I.foo()
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

    class BC implements DI1
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
                    console.print("DC.P.A.foo() this=" + this);
                    super();
                    }
                }

            @Override class C // CANNOT SAY: extends BC.P.C
                {
                @Override
                void foo()
                    {
                    console.print("DC.P.C.foo() this=" + this);
                    super();
                    }
                }
            }
        }
    }