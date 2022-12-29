module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Int i;

        i = Int64.MaxValue;  // 9223372036854775807 (0x7FFFFFFFFFFFFFFF)
        console.println("i=Int64.MaxValue");
        console.println(i);
        console.println(++i);
        console.println(--i);
        console.println();

        i = UInt64.MaxValue;
        console.println("i=UInt64.MaxValue");
        console.println(i);  // 18446744073709551615 (0xFFFFFFFFFFFFFFFF)
        console.println(++i);
        console.println(--i);
        console.println();

        i = Int128.MaxValue;
        console.println("i=Int128.MaxValue");
        console.println(i); // 170141183460469231731687303715884105727
        try
            {
            console.println(++i);
            assert;
            }
        catch (OutOfBounds e) {}
        console.println();

        UInt u = 0;
        try
            {
            console.println(--u);
            assert;
            }
        catch (OutOfBounds e) {}

        u = UInt64.MaxValue;
        console.println("u=UInt64.MaxValue");
        console.println(u);
        console.println(++u);
        console.println(--u);
        console.println();

        u = Int128.MaxValue;
        console.println("Int128.MaxValue");
        console.println(u);
        try
            {
            console.println(++u);
            assert;
            }
        catch (OutOfBounds e) {}
        console.println();

        u = UInt.MaxValue;
        console.println("UInt.MaxValue");
        console.println(u); // 170141183460469231731687303715884105727
        try
            {
            console.println(++u);
            assert;
            }
        catch (OutOfBounds e) {}
        }
    }