module TestSimple
    {
    @Inject Console console;

    void run()
        {
        test(Int, "42");
        test(String, "hello");
        test(IntLiteral, "42");
        test(FPLiteral, "1.23");
        test(Date, Date:2022-12-25.toString());
        test(Time, Time:2024-02-29T12:34:56.toString());
        test(TimeOfDay, TimeOfDay:12:34:56.789.toString());
        test(Duration, Duration:10s.toString());
        test(Duration, "60s");
        test(Duration, "1:11");
        test(Duration, "222:22:22");

        fail(Duration, "3:22:22:22");
        fail(Duration, "3D22:22:22");

        test(Int32, "-255");
        test(UInt32, "12345");
// TODO GG test(Float32, "123.45");
// TODO GG test(Dec64, "-123.45");
        test(Char, "x");
        }

    <Value extends Destringable> void test(Type<Value> type, String text)
        {
        console.println($"type={type}, input={text}, output={new Value(text)}");
        }

    <Value extends Destringable> void fail(Type<Value> type, String text)
        {
        try
            {
            test(type, text);
            }
        catch (Exception e)
            {
            console.println($"type={type}, input={text}, exception: {e}");
            return;
            }
        console.println("DID NOT THROW!!!");
        }
    }