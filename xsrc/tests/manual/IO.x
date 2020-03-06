module TestIO
    {
    import Ecstasy.io.ByteArrayInputStream;
    import Ecstasy.io.CharArrayReader;
    import Ecstasy.io.DataInputStream;
    import Ecstasy.io.InputStream;
    import Ecstasy.io.JavaDataInput;
    import Ecstasy.io.ObjectInput;
    import Ecstasy.io.ObjectOutput;
    import Ecstasy.io.Reader;
    import Ecstasy.io.TextPosition;
    import Ecstasy.io.Writer;
    import Ecstasy.io.UTF8Reader;
    import Ecstasy.web.json.Doc;
    import Ecstasy.web.json.ElementInput;
    import Ecstasy.web.json.ElementOutput;
    import Ecstasy.web.json.FieldInput;
    import Ecstasy.web.json.FieldOutput;
    import Ecstasy.web.json.Lexer;
    import Ecstasy.web.json.Lexer.Token;
    import Ecstasy.web.json.Mapping;
    import Ecstasy.web.json.ObjectInputStream;
    import Ecstasy.web.json.ObjectInputStream.ElementInputStream;
    import Ecstasy.web.json.ObjectInputStream.FieldInputStream;
    import Ecstasy.web.json.ObjectOutputStream;
    import Ecstasy.web.json.ObjectOutputStream.ElementOutputStream;
    import Ecstasy.web.json.Parser;
    import Ecstasy.web.json.Printer;
    import Ecstasy.web.json.Schema;

    @Inject Console console;

    void run()
        {
        testInputStream();
        testJavaUTF();
        testUTF8Reader();
        testJSONLex();
        testJSONParse();
        testJSONPrint();
        testJSONBuild();
        testPoint();
        testMetadata();

        // TODO CP
        // 2020-03-06 08:59:36.539 Service "TestIO" (id=0), fiber 3: Unhandled exception: Ecstasy:web.json.MissingMapping
        //	at web.json.ElementInput.read(Ecstasy:Type<Ecstasy:Object>, Ecstasy:Nullable | read(?)#Serializable) (line=237, op=New_N)
        //	at web.json.ObjectInputStream.read(Ecstasy:Type<Ecstasy:Object>) (line=124, op=Invoke_N1)
        //	at testPointers().testSegment(Ecstasy:web.json.Schema, Ecstasy:String) (line=468, op=Invoke_11)
        //	at testPointers() (line=461, op=Invoke_N0)
        //	at run() (line=55, op=Invoke_00)
        //	at <TestIO> (iPC=0, op=)
        // testPointers();
        }

    void testInputStream()
        {
        console.println("\n*** testInputStream()");

        File    file   = ./IO.x;
        Byte[]  raw    = file.contents;
        var     in     = new ByteArrayInputStream(raw);
        Boolean dotdot = False;
        loop: while (!in.eof)
            {
            Byte b = in.readByte();
            if (loop.count <= 12 || in.remaining <= 2)
                {
                console.println($"[{loop.count}] '{b.toChar()}' ({b})");
                }
            else if (!dotdot)
                {
                console.println("...");
                dotdot = True;
                }
            }
        console.println("(eof)");
        }

    void testJavaUTF()
        {
        console.println("\n*** testJavaUTF()");

        JavaDataInput in = new @JavaDataInput ByteArrayInputStream([0x00, 0x03, 0x43, 0x61, 0x6D]);
        console.println($"string={in.readString()}");
        }

    void testUTF8Reader()
        {
        console.println("\n*** testUTF8Reader()");

        InputStream  inRaw  = new ByteArrayInputStream(#./IO.x);
        UTF8Reader   in     = new UTF8Reader(inRaw);
        Boolean      dotdot = False;
        TextPosition pos    = in.position;
        loop: while (Char ch := in.next())
            {
            if (loop.count <= 20 || inRaw.remaining <= 10)
                {
                console.println($"[{loop.count}] '{ch}' {pos}");
                }
            else if (!dotdot)
                {
                console.println("...");
                dotdot = True;
                }
            pos = in.position;
            }

        console.println($"(eof) position={pos} line={in.lineNumber}");
        }

    static String ExampleJSON =
            `|{
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

    void testJSONLex()
        {
        console.println("\n*** testJSONLex()");

        Reader reader = new CharArrayReader(ExampleJSON);

        Lexer lexer = new Lexer(reader);
        while (Token tok := lexer.next())
            {
            console.println($"token={tok}");
            }

        console.println($"(eof) position={reader.position}");
        }

    void testJSONParse()
        {
        console.println("\n*** testJSONParse()");

        console.println("no dups:");
        Reader reader = new CharArrayReader(ExampleJSON);
        Parser parser = new Parser(reader);
        while (Doc doc := parser.next())
            {
            console.println($"doc={doc}");
            }

        console.println("collate dups:");
        reader = new CharArrayReader(ExampleJSON);
        parser = new Parser(reader);
        parser.collateDups = True;
        while (Doc doc := parser.next())
            {
            console.println($"doc={doc}");
            }
        }

    void testJSONPrint()
        {
        console.println("\n*** testJSONPrint()");

        Reader reader = new CharArrayReader(ExampleJSON);
        Parser parser = new Parser(reader);
        console.println($"raw doc=\n{ExampleJSON}");
        assert Doc doc := parser.next();
        console.println($"doc as structures={doc}");

        console.println($"printing compact={Printer.DEFAULT.render(doc)}");
        console.println($"printing pretty={Printer.PRETTY.render(doc)}");
        console.println($"printing debug={Printer.DEBUG.render(doc)}");
        }

    void testJSONBuild()
        {
        console.println("\n*** testJSONBuild()");

        Schema schema = Schema.DEFAULT;
        StringBuffer writer = new StringBuffer();
        ObjectOutputStream  o_out = schema.createObjectOutput(writer).as(ObjectOutputStream);
        ElementOutputStream e_out = o_out.createElementOutput();
        build(e_out);

        String s = writer.toString();
        console.println($"result={s}");

        Reader reader = new CharArrayReader(s);
        ObjectInputStream o_in = new ObjectInputStream(schema, reader);
//        console.println("BufferedPrinter:");
//        BufferedPrinter p = new BufferedPrinter();
//        build(p);
//        console.println($"doc={p.doc}");
//        console.println($"print ugly={p.toString()}");
//        console.println($"print pretty=\n{p.toString(pretty=True)}");
//
//        console.println("DirectPrinter:");
//        Appender<Char> toConsole = new Appender<Char>()
//            {
//            @Override
//            Appender<Char> add(Char ch)
//                {
//                console.print(ch);
//                return this;
//                }
//            };
//        DirectPrinter p2 = new DirectPrinter(toConsole);
//        build(p2);
//        console.println("\n(done)");
        }

    private void build(ElementOutput builder)
        {
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
            implements Mapping<Point>
        {
        @Override
        String typeName.get()
            {
            return "point";
            }

        @Override
        Point read(ElementInput in)
            {
            using (FieldInput fields = in.openObject())
                {
                return new Point(fields.readInt("x"), fields.readInt("y"));
                }
//                Doc doc = in.readDoc();
//                Map<String, Doc> map = doc.as(Map<String, Doc>);
//                return new Point(map["x"].as(IntLiteral).toInt(), map["y"].as(IntLiteral).toInt()).as(ObjectType);
            }

        @Override
        void write(ElementOutput out, Point value)
            {
            using (FieldOutput fields = out.openObject())
                {
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
            implements Mapping<Segment>
        {
        @Override
        String typeName.get()
            {
            return "segment";
            }

        @Override
        Segment read(ElementInput in)
            {
            using (FieldInput fields = in.openObject())
                {
                return new Segment(fields.read<Point>("p1"), fields.read<Point>("p2"));
                }
            }

        @Override
        void write(ElementOutput out, Segment value)
            {
            using (FieldOutput fields = out.openObject())
                {
                fields.addObject("p1", value.p1)
                      .addObject("p2", value.p2);
                }
            }
        }

    void testPoint()
        {
        console.println("\n*** testPoint()");

        static String ExamplePoint =
                `|  {
                 |  "x" : 31,
                 |  "y" : 7
                 |  }
                ;

        static String ExampleRandom =
                `|  {
                 |  "y" : 7,
                 |  "x" : 31
                 |  }
                ;

        console.println($"json={ExamplePoint}");
        Schema             schema = Schema.DEFAULT;
//        ObjectInputStream  o_in   = schema.createObjectInput(new CharArrayReader(ExamplePoint)); // TODO GG needs better error message
        Reader             reader = new CharArrayReader(ExamplePoint);
        ObjectInputStream  o_in   = schema.createObjectInput(reader).as(ObjectInputStream);
        ElementInputStream e_in   = o_in.ensureElementInput();
        PointMapper        mapper = new PointMapper();
        Point              point  = mapper.read(e_in);
        console.println($"point={point}");

//        Schema schemaSA = new Schema([new PointMapper(), new SegmentMapper()], randomAccess = False);  // TODO GG
        Mapping[] mappings = new Mapping[]; mappings.add(new PointMapper()); mappings.add(new SegmentMapper());
        Schema schemaSA = new Schema(mappings);
        testSer(schemaSA, "point", point);
        testSer(schemaSA, "segment", new Segment(point, new Point(1,99)));

        private <Ser> void testSer(Schema schema, String name, Ser val)
            {
            StringBuffer sb = new StringBuffer();
            schema.createObjectOutput(sb).write(val);

            String s = sb.toString();
            console.println($"JSON {name} written out={s}");

            Ser val2 = schema.createObjectInput(new CharArrayReader(s)).read<Ser>();
            console.println($"read {name} back in={val2}");
            }

        console.println("\n(random access tests)");
        Schema schemaRA = new Schema([new PointMapper()], randomAccess = True);
        testSer(schemaRA, "point", point);
        testDeser("seq-seq", schemaSA, ExamplePoint , False);
        testDeser("seq-rnd", schemaSA, ExampleRandom, True );
        testDeser("rnd-seq", schemaRA, ExamplePoint , False);
        testDeser("rnd-rnd", schemaRA, ExampleRandom, False);

        private void testDeser(String test, Schema schema, String json, Boolean failureExpected)
            {
            try
                {
                Point point = schema.createObjectInput(new CharArrayReader(json)).read<Point>();
                console.println($"read: {point}");
                if (failureExpected)
                    {
                    console.println($"Test \"{test}\" finished, BUT IT SHOULD HAVE FAILED!!!");
                    }
                }
            catch (Exception e)
                {
                if (failureExpected)
                    {
                    console.println($"Test \"{test}\" correctly failed as expected.");
                    }
                else
                    {
                    console.println($"Test \"{test}\" failed with \"{e}\", BUT IT SHOULD NOT HAVE FAILED!!!");
                    }
                }
            }
        }

    void testMetadata()
        {
        console.println("\n*** testMetadata()");

        Schema schema00 = new Schema(randomAccess = False, enableMetadata = False);
        Schema schemaR0 = new Schema(randomAccess = True , enableMetadata = False);
        Schema schema0M = new Schema(randomAccess = False, enableMetadata = True );
        Schema schemaRM = new Schema(randomAccess = True , enableMetadata = True );

        static String Example1 =
                `|  {
                 |  "y" : 7,
                 |  "x" : 31
                 |  }
                ;
        assert openObject(schema00, Example1).metadataFor("$type") == Null;
        assert openObject(schemaR0, Example1).metadataFor("$type") == Null;
        assert openObject(schema0M, Example1).metadataFor("$type") == Null;
        assert openObject(schemaRM, Example1).metadataFor("$type") == Null;

        static String Example2 =
                `|  {
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

        private FieldInputStream openObject(Schema schema, String json)
            {
            Reader             reader = new CharArrayReader(json);
            ObjectInputStream  o_in   = schema.createObjectInput(reader).as(ObjectInputStream);
            ElementInputStream e_in   = o_in.ensureElementInput();
            return e_in.openObject().as(FieldInputStream);
            }
        }

    void testPointers()
        {
        console.println("\n*** testMetadata()");

        Schema schema = new Schema([new PointMapper()], enablePointers = True);

        static String RelativeExample =
                `|  {
                 | "p1": {
                 |       "x" : -1,
                 |       "y" : 28
                 |       },
                 | "p2": {"$ref" : "1/p1"}
                 ;

        static String AbsoluteExample =
                `|  {
                 | "p1": {
                 |       "x" : 1,
                 |       "y" : 9
                 |       },
                 | "p2": {"$ref" : "/p2"}
                 ;

        testSegment(schema, RelativeExample);
        testSegment(schema, AbsoluteExample);

        private void testSegment(Schema schema, String json)
            {
            Reader             reader = new CharArrayReader(json);
            ObjectInputStream  o_in   = schema.createObjectInput(reader).as(ObjectInputStream);
            Segment            segment = o_in.read<Segment>();
            console.println($"json={json}, segment={segment}");
            }
        }
    }