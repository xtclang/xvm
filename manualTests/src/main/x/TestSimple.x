module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Int[] ints = [
                     Int.MinValue, Int.MinValue+1, Int.MinValue+2,
                     -1, 0, 1,
                     Int.MaxValue, Int.MaxValue-1, Int.MaxValue-2
                     ];

        for (Int i : ints)
            {
            Int bitLength=i.bitLength;
            console.println($|{i}, \
                             |bitLength={i.bitLength}, \
                             |bitCount={i.bitCount}, \
                             |trailingZeroCount={i.trailingZeroCount}
                             );
            }
        }
    }