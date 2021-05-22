module Test
    {
    @Inject Console console;
    Log log = new ecstasy.io.ConsoleLog(console);

    void run()
        {
        log.add("Simple property example!");

        val o = new TestDerived();
        for (Int i : 0..5)
            {
            val n = o.x;
            }

        o.&x.foo();
        }

    class TestClass
        {
        Int x
            {
            @Override Int get()
                {
                ++count;
                return super();
                }

            void foo()
                {
                log.add($"Someone accessed this property {count} times!");
                }

            private Int count;
            }
        }

    class TestDerived
            extends TestClass
        {
        @Override Int x
            {
            @Override void foo()
                {
                super();
                log.add($"(And someone called foo() {++count} times!)");
                }

            private Int count;
            }
        }
    }