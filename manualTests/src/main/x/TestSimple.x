module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Test test = new Test();

        Value v = test.getValue();
        console.print($"v={v.is(immutable)}");

        (Value v1, Value v2) = test.getValues();
        console.print($"v1={v1.is(immutable)}, v2={v2.is(immutable)}");
        }

    service Test
        {
        Value getValue() = new Value(1);

        (Value, Value) getValues() = (new Value(2), new Value(3));
        }

    @AutoFreezable
    class Value(Int n) implements Freezable
        {
        @Override
        immutable Value freeze(Boolean inPlace)
            {
            console.print($"Freezing {n}");
            return makeImmutable();
            }
        }
    }