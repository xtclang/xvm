module TestSimple.test.org
    {
    @Inject Console console;

    void run(  )
        {
        new Test<Int, String>();
        }

    class Test<Key, Value>
        {
        immutable Map<Key, Value> bug1 = Map:[];
        immutable Map<Key, Value> bug2 = Map<Key, Value>:[].makeImmutable();
        }
    }
