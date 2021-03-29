module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        new BP().test(); // compiler error
        new DP().test();
        }

    class BP
        {
        void test()
            {
            new C1().foo();
            }

        interface Iface
            {
            void bar();
            }

        class C1
            {
            @Abstract void foo();
            }
        }

    class DP
            extends BP
        {
        @Override
        class C1
            {
            @Override
            void foo()
                {
                console.println("in foo");
                }
            }
        }
    }