module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println(calcTuple());
        }

    Tuple<FutureVar, Int>? calcTuple()
        {
        @Future Tuple r = testTuple();
        return (&r, 1);
        }

    Tuple testTuple()
        {
        return Tuple:();
        }
    }