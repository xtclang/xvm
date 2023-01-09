module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Int[] ints = [
                     MinValue, MinValue+1, MinValue+2,
                     -1, 0, 1,
                     MaxValue, MaxValue-1, MaxValue-2
                     ];

        for (Int i : ints)
            {
            Int bitLength=i.bitLength;
            console.println($|{i}, \
                             |bitLength={i.bitLength}, \
                             |bitCount={i.bitCount}, \
                             |leftmostBit={i >=0 ? i.leftmostBit : -1}, \
                             |rightmostBit={i.rightmostBit}, \
                             |trailingZeroCount={i.trailingZeroCount}
                             );
            }
        }
    }