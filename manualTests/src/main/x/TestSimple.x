module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        DerivedParent.Child0 c = new DerivedParent().new Child1();
        // TODO GG: c.foo();
        assert c.is(Tagged);
        console.println(c.tag);
        }

    interface Tagged
        {
        String tag();
        }

    class BaseParent
        {
        class Child0
            {
            }

        class Child1
                extends Child0
            {
            }
        }

    class DerivedParent
            extends BaseParent
        {
        @Override
        class Child0
                implements Tagged
            {
            void foo()
                {
                console.println("DP.C0");
                }

            @Override String tag()
                {
                return "Amazing";
                }
            }

        @Override
        class Child1
            {
//            @Override  // TODO GG this should override DP.C0
//            void foo()
//                {
//                console.println("DP.C1");
//                super();
//                }
            }
        }
    }