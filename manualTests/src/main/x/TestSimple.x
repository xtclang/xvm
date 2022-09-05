module TestSimple
    {
    @Inject Console console;

    void run()
        {
        test();
        }

    void test(Dec d = 1.0)
        {
        assert 0 <= d <= 1; // this used to assert
        }
    }