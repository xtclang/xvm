module TestSimple
    {
    @Inject Console console;

    package json import json.xtclang.org;

    import ecstasy.io.CharArrayReader;

    import json.Schema;
    import json.ObjectInputStream;

    typedef Int as Key;
    typedef String as Value;

    void run() {
        Boolean[] bools = new Boolean[100];
        bools[10] = True;
        Schema schema = Schema.DEFAULT;

//assert:debug;
        Map<Key, Value> map = new ListMap();
        testSer(schema, "map" , map);

        // all these used to fail at run-time
        testSer(schema, "mapL" , new ListMap<Key, Value>());
        testSer(schema, "mapTL", new TestList(new ListMap<Key, Value>()));
        testSer(schema, "mapTl", new TestMap(new ListMap<Key, Value>()));
        testSer(schema, "mapTH", new TestHash(new HashMap<Key, Value>()));
        testSer(schema, "mapTh", new TestMap(new HashMap<Key, Value>()));

        // these are still failing
//        testSer(schema, "mapLI", new ListMap<Key, Value>().freeze(True));
//        testSer(schema, "mapH" , new HashMap<Key, Value>());
//        testSer(schema, "mapHI", new HashMap<Key, Value>().freeze(True));
        }

    private <Ser> void testSer(Schema schema, String name, Ser val) {
        try
            {
            StringBuffer buf = new StringBuffer();
            schema.createObjectOutput(buf).write(val);

            String s = buf.toString();
            console.print($"JSON {name} written out={s}");

            Ser val2 = schema.createObjectInput(new CharArrayReader(s)).read<Ser>();
            console.print($"read {name} back in={&val2.actualType} {val2}");
            }
        catch (Exception e) {
            console.print($"*** {name} failed with {e}");
        }
    }

    const TestList(ListMap<Key, Value> map);
    const TestHash(HashMap<Key, Value> map);
    const TestMap(Map<Key, Value> map)
        {
        @Override
        String toString()
            {
            return $"{&map.actualType} {map}";
            }
        }
}