module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Int n = 17;
        Bit[] bits = n.toBitArray()[60..64);
        assert (n == 0) == (bits == [0,0,0,0]);
        }
    }