module TestSimple
    {
    @Inject Console console;

    package json import json.xtclang.org;

    import ecstasy.io.CharArrayReader;

    import json.Schema;
    import json.ObjectInputStream;

    typedef String as Key;
    typedef String as Value;

    void run() {
        Schema schema = Schema.DEFAULT;

        Map<Key, Value> map = [];
        testSer(schema, "map" , map);

        Map<Key, Value> mapL = new ListMap();
        testSer(schema, "map(L)" , mapL);

        Map<Key, Value> mapH = new HashMap();
        testSer(schema, "map(H)" , mapH);

        testSer(schema, "mapL" , new ListMap<Key, Value>());
        testSer(schema, "mapTL", new TestList(new ListMap<Key, Value>()));
        testSer(schema, "mapTl", new TestMap(new ListMap<Key, Value>()));
        testSer(schema, "mapTH", new TestHash(new HashMap<Key, Value>()));
        testSer(schema, "mapTh", new TestMap(new HashMap<Key, Value>()));

        // these used to fail
        testSer(schema, "mapLI", new ListMap<Key, Value>().freeze(True));
        testSer(schema, "mapH" , new HashMap<Key, Value>());
        testSer(schema, "mapHI", new HashMap<Key, Value>().freeze(True));
        }

    private <Ser> void testSer(Schema schema, String name, Ser val) {
        try {
            StringBuffer buf = new StringBuffer();
            schema.createObjectOutput(buf).write(val);

            String s = buf.toString();
            console.print($"\nJSON {name} written out={s}");

            Ser val2 = schema.createObjectInput(new CharArrayReader(s)).read<Ser>();
            console.print($"read {name} back in={&val2.actualType} {val2}");
        }
        catch (Exception e) {
            console.print($"*** {name} failed with {e}");
        }
    }

    class TestList(ListMap<Key, Value> map) {
        @Override
        String toString() {
            return $"{&map.actualType} {map}";
        }
    }
    class TestHash(HashMap<Key, Value> map) {
        @Override
        String toString() {
            return $"{&map.actualType} {map}";
        }
    }
    const TestMap(Map<Key, Value> map) {
        @Override
        String toString() {
            return $"{&map.actualType} {map}";
        }
    }
}