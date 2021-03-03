module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        BaseParent.Child0 c = new DerivedParent().new Child1();

        console.println("*** foo");
        c.foo();

        assert c.is(DerivedParent.Child0);
        console.println("\n*** bar");
        c.bar();

        assert c.is(Tagged);
        console.println($"\n*** this is {c.tag}");
        }

    interface Tagged
        {
        @RO String tag;
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
            }
        }

    class DerivedParent
            extends BaseParent
        {
        @Override
        class Child0
                implements Tagged
            {
            @Override
            void foo()
                {
                console.println("DP.C0");
                super();
                }

            void bar()
                {
                console.println("DP.C0");
                }

            @Override String tag.get()
                {
                return "so cool";
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

            @Override
            void bar()
                {
                console.println("DP.C1");
                super();
                }
            }
        }
    }