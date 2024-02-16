module TestSimple {
    @Inject Console console;

    package json import json.xtclang.org;
    import json.*;

    void run() {
      Schema schema = Schema.DEFAULT;

      Tuple<Int, String, Int> tuple = (1, "a", 2);
      testSer(schema, "tuple", tuple);

      tuple = (1, "a", 2);
      testSer(schema, "tuple", tuple); // this used to fail
    }

    private <Ser> void testSer(Schema schema, String name, Ser val) {
        StringBuffer buf = new StringBuffer();
        schema.createObjectOutput(buf).write(val);

        String s = buf.toString();
        console.print($"JSON {name} written out={s}");

        Ser val2 = schema.createObjectInput(s.toReader()).read<Ser>();
        console.print($"read {name} back in={val2}");
    }
}

