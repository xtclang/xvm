module TestSimple
    {
    @Inject Console console;

    package json import json.xtclang.org;

    import ecstasy.io.*;
    import json.*;

    void run()
        {
        Schema schema = Schema.DEFAULT;

//        HashMap<String, String> map = new HashMap();
//        map = map.put(1, "a");
//        map = map.put(2, "b");
//        testSer(schema, "map", new Test(map));

        immutable String[] ints = ["a", "b"].freeze(True);
        testSer(schema, "ints", ints);
        }

    private <Ser> void testSer(Schema schema, String name, Ser val)
        {
        StringBuffer buf = new StringBuffer();
        schema.createObjectOutput(buf).write(val);

        String s = buf.toString();
        console.println($"JSON {name} written out={s}");

        Ser val2 = schema.createObjectInput(new CharArrayReader(s)).read<Ser>();
        console.println($"read {name} back in={val2} ({&val2.actualType})");
        }
    }