module TestSimple
    {
    @Inject Console console;
    @Inject Random  random;

    void run(   )
        {
        }

    Char addTrailingSurrogate(Char trailing)
        {
        UInt32 hi = 2;
        UInt32 lo = 1;
        return new Char(0x010000 + (hi - 0xD800 << 10) + lo - 0xDC00);
        }
    }