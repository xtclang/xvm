module TestSimple
    {
    @Inject Console console;

    void run()
        {
        }

    class Test
        {
        // used to fail to compile (a small reproducer of the actual failure at Time.x that Marcus ran into)
        static Int128 GREGORIAN_OFFSET = Date1.GREGORIAN_OFFSET * TimeOfDay1.PICOS_PER_DAY;
        }

    const Date1(Int32 epochDay)
            implements Sequential
        {
        static IntLiteral GREGORIAN_OFFSET  = 0;
        }

    const TimeOfDay1(UInt64 picos)
        {
        static IntLiteral PICOS_PER_DAY     = 0;
        }
    }