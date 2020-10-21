module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Test<String, IntNumber> t = new Test(new HashMap());
        t.test("a");
        }

    class Test<Key, Value>(Map<Key, Value> map)
        {
        void test(Key key)
            {
            Int r = map.process(key, entry ->
                {
                @Inject Console console;
                console.println(&entry.Referent);
                console.println(&entry.actualType);
                return 0;
                });
            }
        }
    }
