module TestSimple.xqiz.it
    {
    @Inject ecstasy.io.Console console;

    void run(   )
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

        @M
        class TestA
                implements Iface
            {
            @Override
            void f(Boolean flag = false)
                {
                console.println($"TestA:f {flag}");
                super(); // this should not compile
                }

            void other()
                {
                f(true);
                }
            }

        class TestDerivedA
                extends TestA
            {
            @Override
            void f(Boolean flag=false)
                {
                console.println($"TestDA:f {flag}");
                super(flag);
                }
            }

        mixin M
            into TestA
            {
            @Override
            void f(Boolean flag=false)
                {
                console.println($"M:f {flag}");
                super(flag);
                }
            }
        }
    }
