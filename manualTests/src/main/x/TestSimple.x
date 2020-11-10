module TestSimple
    {
    @Inject Console console;

    import ecstasy.io.CharArrayReader;

    package json import json.xtclang.org;

    import json.Mapping;
    import json.Schema;

    void run()
        {
        Schema schema = Schema.DEFAULT;
        Point  point  = new Point(1, 2);

        console.println($"point={point}");

        testSer(schema, "point", point);
        }

    private <Ser> void testSer(Schema schema, String name, Ser val)
        {
        StringBuffer buf = new StringBuffer();
        schema.createObjectOutput(buf).write(val);

        String s = buf.toString();
        console.println($"JSON {name} written out={s}");

        Ser val2 = schema.createObjectInput(new CharArrayReader(s)).read<Ser>();
        console.println($"read {name} back in={val2}");
        }

    const Point(Int x, Int y);
    }
