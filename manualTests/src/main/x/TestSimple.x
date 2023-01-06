module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Int64 i64 = MinValue;
        try
            {
            console.println(i64.abs()); // should blow; used to produce an incorrect result
            assert;
            }
        catch (OutOfBounds e) {}

        Int32 i32 = MinValue;
        try
            {
            console.println(i32.abs());
            assert;
            }
        catch (OutOfBounds e) {}
        }
    }