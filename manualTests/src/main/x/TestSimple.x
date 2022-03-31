module TestSimple
    {
    @Inject Console console;

    enum TrustLevel {None, Normal, High, Highest}

    void run()
        {
        TrustLevel t1 = High;
        TrustLevel t2 = t1.minOf(Normal); // used to fail to compile
        }
    }