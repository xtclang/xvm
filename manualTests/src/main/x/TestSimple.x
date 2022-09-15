module TestSimple
    {
    @Inject Console console;

    void run()
        {
        if (Int i := test().as(Int)) // this used to assert in the compiler
            {
            }
        }

    conditional Number test()
        {
        return False;
        }
    }