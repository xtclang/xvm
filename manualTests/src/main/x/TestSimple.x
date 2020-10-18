module TestSimple
    {
    @Inject Console console;

    void run()
        {
        new Test().report();
        }

    mixin Silly
            into Var<Int>
        {
        }

    class Test
        {
        @Lazy(() -> foo()) Int prop;

        static Int foo()
            {
            return 42;
            }

        @Lazy Int prop2.calc()
            {
            return 48;
            }

        @Silly Int prop3 = 3;

        void report()
            {
            console.println(prop);
            console.println(&prop);

            console.println(prop2);
            console.println(&prop2);

            console.println(prop3);
            console.println(&prop3);
            }
        }
    }
