module TestSimple
    {
    @Inject Console console;

    void run()
        {
        TestApp t = new TestApp();
        t.report();
        console.println(t.p2);
        }

    class TestApp
            extends BaseApp
        {
        }

    class BaseApp
        {
        void report()
            {
            console.println(p1);
            }

        @Lazy protected/private Int p1.calc()
            {
            return 42;
            }

        @Lazy public/private Int p2.calc()
            {
            return 43;
            }
        }
    }
