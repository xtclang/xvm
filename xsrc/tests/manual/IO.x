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

    void testPoint()
        {
        static const Point(Int x, Int y);

        static const PointMapper
                implements Mapping<Point>
            {
            @Override
            String typeName.get()
                {
                return "point";
                }

            @Override
            <ObjectType extends Point> ObjectType read<ObjectType>(ElementInput in)
                {
                using (FieldInput fields = in.openObject())
                    {
                    return new Point(fields.readInt("x"), fields.readInt("y")).as(ObjectType);
                    }
//                Doc doc = in.readDoc();
//                Map<String, Doc> map = doc.as(Map<String, Doc>);
//                return new Point(map["x"].as(IntLiteral).toInt(), map["y"].as(IntLiteral).toInt()).as(ObjectType);
                }

            @Override
            <ObjectType extends Point> void write(ElementOutput out, ObjectType value)
                {
                out.openObject()
                    .add("x", value.x)
                    .add("y", value.y)
                    .close();
                }
            }

        static String ExamplePoint =
                `|  {
                 |  "x" : 31,
                 |  "y" : 7
                 |  }
                ;

        console.println($"json={ExamplePoint}");
        Schema             schema = Schema.DEFAULT;
//        ObjectInputStream  o_in   = schema.createObjectInput(new CharArrayReader(ExamplePoint)); // TODO GG needs better error message
        Reader             reader = new CharArrayReader(ExamplePoint);
        ObjectInputStream  o_in   = schema.createObjectInput(reader).as(ObjectInputStream);
        ElementInputStream e_in   = o_in.createElementInput();
        PointMapper        mapper = new PointMapper();
        Point              point  = mapper.read<Point>(e_in);
        console.println($"point={point}");

        Schema schemaRA = new Schema([new PointMapper()], randomAccess = True);
        Schema schemaSA = new Schema([new PointMapper()], randomAccess = False);

//        Point point1 = schemaRA.createObjectInput(new CharArrayReader(ExamplePoint)).read<Point>();
//        console.println($"point={point1}");
        Point point2 = schemaSA.createObjectInput(new CharArrayReader(ExamplePoint)).read<Point>();
        console.println($"point={point2}");
        }
    }