module TestSimple
    {
    @Inject Console console;

    void run()
        {
        immutable Int32[] ints = [1, 2, 3];
        Int64 n = 4;
        for (Int64 p : ints)
            {
            assert p != n; // used to fail to compile
            }
        }
    }