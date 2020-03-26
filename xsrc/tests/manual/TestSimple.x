module TestSimple.xqiz.it
    {
    @Inject ecstasy.io.Console console;

    void run()
        {
        new Parent().test();
        }

    interface Iface
        {
        Iface f();
        }

    class Parent
        {
        void test()
            {
            TestDerivedA test = new TestDerivedA();
            test.f();
            console.println();
            test.other();
            }

        @M
        class TestA
                implements Iface
            {
            @Override
            TestA f(Boolean flag = false)
                {
                console.println($"TestA:f {flag}");
                return this;
                }

            void other()
                {
                f(true);
                }
            }

        @M
        class TestB
                implements Iface
            {
            @Override
            TestB f(Boolean flag = false)
                {
                console.println($"TestB:f {flag}");
                return this;
                }
            }

        class TestDerivedA
                extends TestA
            {
            @Override
            TestDerivedA f(Boolean flag=false)
                {
                console.println($"TestDA:f {flag}");
                return super(flag);
                }
            }

        mixin M
            into (TestA | TestB)
            {
            @Override
            M f(Boolean flag=false)
                {
                console.println($"M:f {flag}");
                return super(flag);
                }

            // TODO GG: this shouldn't compile - needs a @Override
            void other()
                {
                console.println($"M:other");
                super();
                }
            }
        }
    }
