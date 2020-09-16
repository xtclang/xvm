module TestSimple
    {
    @Inject Console console;

    void run()
        {
        pkg.foo();
        }

    package pkg
        {
        static void foo()
            {
            TestSimple.console.println("foo");
            }
        }
    }
