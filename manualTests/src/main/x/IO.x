module TestIO {
    import ecstasy.io.ByteArrayInputStream;
    import ecstasy.io.ByteArrayOutputStream;
    import ecstasy.io.CharArrayReader;
    import ecstasy.io.DataInputStream;
    import ecstasy.io.InputStream;
    import ecstasy.io.JavaDataInput;
    import ecstasy.io.ObjectInput;
    import ecstasy.io.ObjectOutput;
    import ecstasy.io.PackedDataInput;
    import ecstasy.io.PackedDataOutput;
    import ecstasy.io.Reader;
    import ecstasy.io.TextPosition;
    import ecstasy.io.Writer;
    import ecstasy.io.UTF8Reader;

    package json import json.xtclang.org;

    import json.Doc;
    import json.ElementInput;
    import json.ElementOutput;
    import json.FieldInput;
    import json.FieldOutput;
    import json.Lexer;
    import json.Lexer.Token;
    import json.Mapping;
    import json.ObjectInputStream;
    import json.ObjectInputStream.ElementInputStream;
    import json.ObjectInputStream.FieldInputStream;
    import json.ObjectOutputStream;
    import json.ObjectOutputStream.ElementOutputStream;
    import json.Parser;
    import json.Printer;
    import json.Schema;

    @Inject Console console;

    void run() {
        testInputStream();
        testPacked();
        testJavaUTF();
        testUTF8Reader();
        testJSONLex();
        testJSONParse();
        testJSONPrint();
        testJSONBuild();
        testPoint();
        testTuple();
        testMap();
        testMetadata();
        testPointers();
    }

    void testInputStream() {
        console.print("\n*** testInputStream()");

        File    file   = ./IO.x;
        Byte[]  raw    = file.contents;
        var     in     = new ByteArrayInputStream(raw);
        Boolean dotdot = False;
        loop: while (!in.eof) {
            Byte b = in.readByte();
            if (loop.count <= 12 || in.remaining <= 2) {
                console.print($"[{loop.count}] {b.toChar().quoted()} ({b})");
            } else if (!dotdot) {
                console.print("...");
                dotdot = True;
            }
        }
        console.print("(eof)");
    }

    void testPacked() {
        console.print("\n*** testPacked()");

        @PackedDataOutput ByteArrayOutputStream out = new @PackedDataOutput ByteArrayOutputStream();
        for (Int64 i : -150 .. +150) {
            out.writeInt64(i);
        }

        Int[] others = [Int64.MinValue, Int64.MaxValue, -12341235, -1234151515, +1324153, +1512358723597];
        for (Int i : others) {
            out.writeInt64(i.toInt64());
        }

        for (Int64 i = 1; i > 0; i = i << 1) {
            out.writeInt64(i);
            out.writeInt64(i+1);
            out.writeInt64(i-1);
            out.writeInt64(-i);
            out.writeInt64(-(i+1));
            out.writeInt64(-(i-1));
        }

        @PackedDataInput ByteArrayInputStream in = new @PackedDataInput ByteArrayInputStream(out.bytes);
        for (Int64 i : -150 .. +150) {
            assert in.readInt64() == i;
        }
        for (Int i : others) {
            assert in.readInt64() == i;
        }

        for (Int64 i = 1; i > 0; i = i << 1) {
            assert in.readInt64() == i;
            assert in.readInt64() == i+1;
            assert in.readInt64() == i-1;
            assert in.readInt64() == -i;
            assert in.readInt64() == -(i+1);
            assert in.readInt64() == -(i-1);
        }
    }

    void testJavaUTF() {
        console.print("\n*** testJavaUTF()");

        JavaDataInput in = new @JavaDataInput ByteArrayInputStream([0x00, 0x03, 0x43, 0x61, 0x6D]);
        console.print($"string={in.readString()}");
    }

    void testUTF8Reader() {
        console.print("\n*** testUTF8Reader()");

        InputStream  inRaw  = new ByteArrayInputStream(#./IO.x);
        UTF8Reader   in     = new UTF8Reader(inRaw);
        Boolean      dotdot = False;
        TextPosition pos    = in.position;
        loop: while (Char ch := in.next()) {
            if (loop.count <= 20 || inRaw.remaining <= 10) {
                console.print($"[{loop.count}] {ch.quoted()} {pos}");
            } else if (!dotdot) {
                console.print("...");
                dotdot = True;
            }
            pos = in.position;
        }

        console.print($"(eof) position={pos} line={in.lineNumber}");
        }

    static String ExampleJSON =
            \|{
             |   "name" : "Bob",
             |   "age" : 23,
             |   "married" : true,
             |   "parent" : false,
             |   "reason" : null,
             |   "fav_nums" : [ 17, 42 ],
             |   "probability" : 0.10,
             |   "dog" :
             |      {
             |      "name" : "Spot",
             |      "age" : 7,
             |      "name" : "George"
             |      }
             |}
            ;

    void testJSONLex() {
        console.print("\n*** testJSONLex()");

        Reader reader = new CharArrayReader(ExampleJSON);

        Lexer lexer = new Lexer(reader);
        while (Token tok := lexer.next()) {
            console.print($"token={tok.toDebugString()}");
            }

        console.print($"(eof) position={reader.position}");
    }

    void testJSONParse() {
        console.print("\n*** testJSONParse()");

        console.print("no dups:");
        Reader reader = new CharArrayReader(ExampleJSON);
        Parser parser = new Parser(reader);
        while (Doc doc := parser.next()) {
            console.print($"{doc=}");
        }

        console.print("collate dups:");
        reader = new CharArrayReader(ExampleJSON);
        parser = new Parser(reader);
        parser.collateDups = True;
        while (Doc doc := parser.next()) {
            console.print($"{doc=}");
        }
    }

    void testJSONPrint() {
        console.print("\n*** testJSONPrint()");

        Reader reader = new CharArrayReader(ExampleJSON);
        Parser parser = new Parser(reader);
        console.print($"raw doc=\n{ExampleJSON}");
        assert Doc doc := parser.next();
        console.print($"doc as structures={doc}");

        console.print($"printing compact={Printer.DEFAULT.render(doc)}");
        console.print($"printing pretty={Printer.PRETTY.render(doc)}");
        console.print($"printing debug={Printer.DEBUG.render(doc)}");
    }

    void testJSONBuild() {
        console.print("\n*** testJSONBuild()");

        Schema schema = Schema.DEFAULT;
        StringBuffer writer = new StringBuffer();
        ObjectOutputStream  o_out = schema.createObjectOutput(writer).as(ObjectOutputStream);
        ElementOutputStream e_out = o_out.createElementOutput();
        build(e_out);

        String s = writer.toString();
        console.print($"result={s}");

        Reader reader = new CharArrayReader(s);
        ObjectInputStream o_in = new ObjectInputStream(schema, reader);
//        console.print("BufferedPrinter:");
//        BufferedPrinter p = new BufferedPrinter();
//        build(p);
//        console.print($"doc={p.doc}");
//        console.print($"print ugly={p.toString()}");
//        console.print($"print pretty=\n{p.toString(pretty=True)}");
//
//        console.print("DirectPrinter:");
//        Appender<Char> toConsole = new Appender<Char>()
//            {
//            @Override
//            Appender<Char> add(Char ch)
//                {
//                console.print(ch, suppressNewline=True);
//                return this;
//                }
//            };
//        DirectPrinter p2 = new DirectPrinter(toConsole);
//        build(p2);
//        console.print("\n(done)");
    }

    private void build(ElementOutput builder) {
        builder.openObject()
                .add("$schema", "http://json-schema.org/schema#")
                .add("title", "Product")
                .add("type", "object")
                .addArray("required", ["id", "name", "price"])
                .addArray("numbers", [1,2,3])
                .openObject("properties")
                    .openObject("id")
                        .add("type", "number")
                        .add("description", "Product identifier")
                    .close()
                    .openObject("name")
                        .add("type", "string")
                        .add("description", "Name of the product")
                    .close()
                .close()
                .close();
    }

    const Point(Int x, Int y);
    const Segment(Point p1, Point p2);

    const PointMapper
            implements Mapping<Point> {
        @Override
        String typeName.get() {
            return "point";
        }

        @Override
        Point read(ElementInput in) {
            using (FieldInput fields = in.openObject()) {
                return new Point(fields.readInt("x"), fields.readInt("y"));
//console.print($"reading point");
//Int x = fields.readInt("x");
//console.print($"{x=}");
//Int y = fields.readInt("y");
//console.print($"{y=}");
//return new Point(x, y);
            }
//                Doc doc = in.readDoc();
//                Map<String, Doc> map = doc.as(Map<String, Doc>);
//                return new Point(map["x"].as(IntLiteral).toInt64(), map["y"].as(IntLiteral).toInt64()).as(ObjectType);
        }

        @Override
        void write(ElementOutput out, Point value) {
            using (FieldOutput fields = out.openObject()) {
                fields.add("x", value.x)
                      .add("y", value.y);
            }
//                out.openObject()
//                    .add("x", value.x)
//                    .add("y", value.y)
//                    .close();
        }
    }

    const SegmentMapper
            implements Mapping<Segment> {
        @Override
        String typeName.get() {
            return "segment";
        }

        @Override
        Segment read(ElementInput in) {
            using (FieldInput fields = in.openObject()) {
                return new Segment(fields.readObject<Point>("p1"), fields.readObject<Point>("p2"));
//console.print($"reading segment");
//Point p1 = fields.read<Point>("p1");
//console.print($"{p1=}");
//Point p2 = fields.read<Point>("p2");
//console.print($"{p2=}");
//return new Segment(p1, p2);
            }
        }

        @Override
        void write(ElementOutput out, Segment value) {
            using (FieldOutput fields = out.openObject()) {
                fields.addObject("p1", value.p1)
                      .addObject("p2", value.p2);
            }
        }
    }

    private <Ser> void testSer(Schema schema, String name, Ser val) {
        StringBuffer buf = new StringBuffer();
        schema.createObjectOutput(buf).write(val);

        String s = buf.toString();
        console.print($"JSON {name} written out={s}");

        Ser val2 = schema.createObjectInput(new CharArrayReader(s)).read<Ser>();
        console.print($"read {name} back in={val2}");
    }

    void testPoint() {
        console.print("\n*** testPoint()");

        static String ExamplePoint =
                \|  {
                 |  "x" : 31,
                 |  "y" : 7
                 |  }
                ;

        static String ExampleRandom =
                \|  {
                 |  "y" : 7,
                 |  "x" : 31
                 |  }
                ;

        console.print($"json={ExamplePoint}");
        Schema             schema = Schema.DEFAULT;
        Reader             reader = new CharArrayReader(ExamplePoint);
        ObjectInputStream  o_in   = schema.createObjectInput(reader).as(ObjectInputStream);
        ElementInputStream e_in   = o_in.ensureElementInput();
        PointMapper        mapper = new PointMapper();
        Point              point  = mapper.read(e_in);
        console.print($"{point=}");

        Schema schemaSA = new Schema([new PointMapper(), new SegmentMapper()]);
        testSer(schemaSA, "point", point);
        testSer(schemaSA, "segment", new Segment(point, new Point(1,99)));

        // test ReflectionMapping
        testSer(schema, "point", point);

        console.print("\n(random access tests)");
        Schema schemaRA = new Schema([new PointMapper()], randomAccess = True);
        testSer(schemaRA, "point", point);
        testDeser("seq-seq", schemaSA, ExamplePoint , False);
        testDeser("seq-rnd", schemaSA, ExampleRandom, True );
        testDeser("rnd-seq", schemaRA, ExamplePoint , False);
        testDeser("rnd-rnd", schemaRA, ExampleRandom, False);

        private void testDeser(String test, Schema schema, String json, Boolean failureExpected) {
            try {
                Point point = schema.createObjectInput(new CharArrayReader(json)).read<Point>();
                console.print($"read: {point}");
                if (failureExpected)
                    {
                    console.print($"Test \"{test}\" finished, BUT IT SHOULD HAVE FAILED!!!");
                    }
            } catch (Exception e) {
                if (failureExpected) {
                    console.print($"Test \"{test}\" correctly failed as expected.");
                } else {
                    console.print($"Test \"{test}\" failed with \"{e}\", BUT IT SHOULD NOT HAVE FAILED!!!");
                }
            }
        }
    }

    void testTuple() {
        console.print("\n*** testTuple()");

        Schema schema = Schema.DEFAULT;

        Tuple<Int, String> tuple = (1, "a");
        testSer(schema, "tuple", tuple);
        testSer(schema, "tuple", new Test(tuple));

        const Test(Tuple<Int, String> tuple);
    }

    void testMap() {
        console.print("\n*** testMap()");

        Schema schema = Schema.DEFAULT;

        Map<Int, String> map1 = new HashMap();
        map1 = map1.put(1, "a");
        map1 = map1.put(2, "b");
        testSer(schema, "map", new Test<Int, String>(map1));
        testSer(schema, "map", map1);

        Map<String, Int> map2 = Map:["a"=1, "b"=2];
        testSer(schema, "map", new Test<String, Int>(map2));
        testSer(schema, "map", map2);
        Map<String, Int>? map3 = map2;
        testSer(schema, "map", map3);

        String jsonMap = \|{"a":1, "b":2}
                          ;
        Map<String, Int> map4 = schema.createObjectInput(new UTF8Reader(
                                    new ByteArrayInputStream(jsonMap.utf8()))).read();
        assert map4 == map2;

        const Test<Key, Value>(Map<Key, Value> map);
    }

    void testMetadata() {
        console.print("\n*** testMetadata()");

        Schema schema00 = new Schema(randomAccess = False, enableMetadata = False);
        Schema schemaR0 = new Schema(randomAccess = True , enableMetadata = False);
        Schema schema0M = new Schema(randomAccess = False, enableMetadata = True );
        Schema schemaRM = new Schema(randomAccess = True , enableMetadata = True );

        static String Example1 =
                \|  {
                 |  "y" : 7,
                 |  "x" : 31
                 |  }
                ;
        assert openObject(schema00, Example1).metadataFor("$type") == Null;
        assert openObject(schemaR0, Example1).metadataFor("$type") == Null;
        assert openObject(schema0M, Example1).metadataFor("$type") == Null;
        assert openObject(schemaRM, Example1).metadataFor("$type") == Null;

        static String Example2 =
                \|  {
                 |  "$type" : "point",
                 |  "y" : 7,
                 |  "x" : 31,
                 |  "$id" : "whatever"
                 |  }
                ;
        assert openObject(schema00, Example2).metadataFor("$type") == Null;
        assert openObject(schemaR0, Example2).metadataFor("$type") == Null;
        assert openObject(schema0M, Example2).metadataFor("$type") == "point";
        assert openObject(schemaRM, Example2).metadataFor("$type") == "point";

        assert openObject(schema00, Example2).metadataFor("$id") == Null;
        assert openObject(schemaR0, Example2).metadataFor("$id") == Null;
        assert openObject(schema0M, Example2).metadataFor("$id") == Null;
        assert openObject(schemaRM, Example2).metadataFor("$id") == "whatever";

        private FieldInputStream openObject(Schema schema, String json) {
            Reader             reader = new CharArrayReader(json);
            ObjectInputStream  o_in   = schema.createObjectInput(reader).as(ObjectInputStream);
            ElementInputStream e_in   = o_in.ensureElementInput();
            return e_in.openObject().as(FieldInputStream);
        }
    }

    void testPointers() {
        console.print("\n*** testPointers()");

        Mapping[] mappings = new Mapping[]; mappings.add(new PointMapper()); mappings.add(new SegmentMapper());
        Schema schema = new Schema(mappings, enablePointers = True);

        static String RelativeExample =
                \| {
                 | "p1": {
                 |       "x" : -1,
                 |       "y" : 28
                 |       },
                 | "p2": {"$ref" : "1/p1"}
                 | }
                 ;

        static String AbsoluteExample =
                \| {
                 | "p1": {
                 |       "x" : 1,
                 |       "y" : 9
                 |       },
                 | "p2": {"$ref" : "/p1"}
                 | }
                 ;

        testSegment(schema, RelativeExample);
        testSegment(schema, AbsoluteExample);

        private void testSegment(Schema schema, String json) {
            console.print($"{json=}");
            Reader             reader = new CharArrayReader(json);
            ObjectInputStream  o_in   = schema.createObjectInput(reader).as(ObjectInputStream);
            Segment            segment = o_in.read<Segment>();
            console.print($"{segment=}");
        }
    }

    static String ReflectionExample =
            \| {
             | "$type" : "Segment",
             | "p1": {
             |       "$type" : "Point",
             |       "x" : -1,
             |       "y" : 28
             |       },
             | "p2": {
             |       "x" : 77,
             |       "y" : 98
             |       },
             | }
             ;
}