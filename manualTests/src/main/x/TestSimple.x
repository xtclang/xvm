module TestSimple
    {
    @Inject Console console;

    void run()
        {
        test(Int, "42");
        test(String, "hello");
        test(IntLiteral, "42");
        test(FPLiteral, "1.23");
        }

    <Value extends Destringable> void test(Type<Value> type, String text)
        {
        console.println($"type={type}, input={text}, output={new Value(text)}");
        }
    }