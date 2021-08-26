module TestSimple.test.org
    {
    package json import json.xtclang.org;

    import ecstasy.io.CharArrayReader;
    import json.*;

    @Inject Console console;

    void run()
        {
        Element[] els = new Element[];
        els.add(new Element("A"));
        els.add(new Element("B"));

        Data d = new Data("a", [0, 1, 2], els);
        console.println(d);

        Schema schema = Schema.DEFAULT;

        StringBuffer buf = new StringBuffer();
        schema.createObjectOutput(buf).write(d);

        String s = buf.toString();
        console.println($"written out={s}");

        Data d2 = schema.createObjectInput(new CharArrayReader(s)).read<Data>();
        console.println($"read back in={d2}");

        }

    const Data(String s, Int[] ints, Element[] els);
    const Element(String s);
    }
