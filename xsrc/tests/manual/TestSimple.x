module TestSimple.xqiz.it
    {
    @Inject ecstasy.io.Console console;

    void run()
        {
        new Container().test();
        }

    interface Iface
        {
        void f();
        }

    class Container
        {
        void test()
            {
            TestA test = new TestDerivedA();
            test.f();
            console.println();
            test.other();
            }

        class TestBase
                implements Iface
            {
            @Override
            void f()
                {
                console.println("TestBase:f");
                }
            }

        @M
        class TestA
                extends TestBase
            {
            @Override
            void f()
                {
                console.println("TestA:f");
                super();
                }

            void other()
                {
                f();
                }
            }

        class TestDerivedA
                extends TestA
            {
            @Override
            void f()
                {
                console.println("TestDA:f");
                super();
                }
            }

        mixin M
            into TestA
            {
            @Override
            void f()
                {
                console.println("M:f");
                super();
                }
            }
        }
    }
