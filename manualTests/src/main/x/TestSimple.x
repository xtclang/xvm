module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Parent<String> p = new Parent();
        p.foo();
        }

    class Parent<Element>
        {
        void foo()
            {
            // this used to "soft assert" with "ERROR: Unresolved type ..."
            Work w = FakeWork;
            }

        protected static interface Work
            {
            Int getWork(Int i);
            }

        protected static Work FakeWork = new Work()
            {
            @Override
            Int getWork(Int i)
                {
                assert;
                }
            };
        }
    }