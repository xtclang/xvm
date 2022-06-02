module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.println("Hello atomics!");

        @Atomic Int i64 = 42;
        i64++;
        i64--;
        assert i64 == 42;

        @Atomic UInt ui64 = 42;
        ui64++;
        ui64--;
        assert ui64 == 42;

        @Atomic Int32 ui32 = 42;
        ui32++;
        ui32--;
        assert ui32 == 42;
        }
    }