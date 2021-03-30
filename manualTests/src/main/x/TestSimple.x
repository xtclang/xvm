module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        console.println($"{new Tests().foo()}");
        }

    class Tests
        {
        String foo()
            {
            return value;

            private @Lazy String value.calc()
                {
                return "hello";
                }
            }
        }
    }