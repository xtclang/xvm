module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Int n = 14;
        Bit[]     bits  = n.toBitArray();
        Boolean[] bools = n.toBooleanArray();

        for (Int i : 0..63)
            {
            assert (bits[i] == 1) == bools[i];
            }
        console.println(bits);
        console.println(bools);
        }
    }