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

        Tuple<Value, Value> t1 = test.getValues();
        console.print($"t1={t1.is(immutable)}, t1[0]={t1[0].is(immutable)}, t1[1]={t1[1].is(immutable)}");

        Tuple t2 = test.getValuesAsTuple();
        console.print($"t2={t2.is(immutable)}, t2[0]={t2[0].is(immutable)}, t2[1]={t2[1].is(immutable)}");

        test.setValue(new Value(7));
        }

    service Test
        {
        Value getValue() = new Value(1);

        (Value, Value) getValues() = (new Value(2), new Value(3));

        Tuple getValuesAsTuple() = (new Value(4), new Value(5), -1);

        void setValue(Value v)
            {
            console.print($"setValue: {v.is(immutable)}");
            }
        }

    @AutoFreezable
    class Value(Int n) implements Freezable
        {
        @Override
        immutable Value freeze(Boolean inPlace)
            {
            console.print($"Freezing {n=}; {inPlace=}");
            return makeImmutable();
            }
        }
    }