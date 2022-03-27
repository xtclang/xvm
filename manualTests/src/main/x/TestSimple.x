module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Int i = value;
        new Test().test();
        }

    @Lazy Int value.calc()
        {
        console.println("in calc");  // this used to be executed twice!!?
        return 42;
        }

    service Test
        {
        void test()
            {
            Int i = TestSimple.value;
            }
        }
    }