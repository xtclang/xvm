module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        console.println("\n-> BP.C2");
        new BaseParent().new Child2().foo();

        console.println("\n-> DP.C0");
        new DerivedParent().new Child0().foo();

        console.println("\n-> DP.C1");
        new DerivedParent().new Child1().foo();

        console.println("\n-> DP.C2");
        new DerivedParent().new Child2().foo();
        }

    class BaseParent
        {
        class Child0
            {
            void foo()
                {
                console.println("BP.C0");
                }
            }

        class Child1
                extends Child0
            {
            @Override
            void foo()
                {
                console.println("BP.C1");
                super();
                }
            }

        class Child2
                extends Child1
            {
            @Override
            void foo()
                {
                console.println("BP.C2");
                super();
                }
            }
        }

    class DerivedParent
            extends BaseParent
        {
        @Override
        class Child0
            {
            @Override
            void foo()
                {
                console.println("DP.C0");
                super();
                }
            }

        @Override
        class Child1
            {
            @Override
            void foo()
                {
                console.println("DP.C1");
                super();
                }
            }

        @Override
        class Child2
            {
//            @Override
//            void foo()
//                {
//                console.println("DP.C2");
//                super();
//                }
            }
        }
    }