module TestSimple
    {
    @Inject Console console;

    void run()
        {
        assert Int value := test().as(Int); // this used to assert in the compiler
        }

    conditional IntNumber test()
        {
        return True, Int:42;
        }
    }