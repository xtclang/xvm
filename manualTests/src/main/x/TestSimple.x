module TestSimple {
    @Inject Console console;

    package json import json.xtclang.org;
    import json.*;

    void run() {

        Schema schema = Schema.DEFAULT;

        test1(schema, True);
        test2(schema);
        test3(schema, Num.values[1]);
        test4(schema);
    }

    void test1(Schema schema, Boolean f) {
       Tuple<Int, Boolean> tuple = (1, f);
       testSer(schema, "tuple", tuple); // used to fail the deserialization
    }

    void test2(Schema schema) {
       Tuple<Int, Boolean> tuple = (1, True);
       testSer(schema, "tuple", tuple); // used to fail the deserialization
    }

    void test3(Schema schema, Num n) {
       Tuple<Int, Num> tuple = (1, n);
       testSer(schema, "tuple", tuple); // used to fail the deserialization
    }

    void test4(Schema schema) {
       Tuple<Int, Num> tuple = (1, Two);
       testSer(schema, "tuple", tuple); // used to fail the deserialization
    }

    enum Num {One, Two, Three}

    private <Ser> void testSer(Schema schema, String name, Ser val) {
        StringBuffer buf = new StringBuffer();
        schema.createObjectOutput(buf).write(val);

        String s = buf.toString();
        console.print($"JSON {name} written out={s}");

        Ser val2 = schema.createObjectInput(s.toReader()).read<Ser>();
        console.print($"read {name} back in={val2}");
    }
}

