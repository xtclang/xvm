module TestIO
    {
    import Ecstasy.io.ByteArrayInputStream;
    import Ecstasy.io.CharArrayReader;
    import Ecstasy.io.DataInputStream;
    import Ecstasy.io.InputStream;
    import Ecstasy.io.JavaDataInput;
    import Ecstasy.io.Reader;
    import Ecstasy.io.UTF8Reader;
    import Ecstasy.web.json.Lexer;
    import Ecstasy.web.json.Lexer.Token;

    @Inject Console console;

    void run()
        {
        testInputStream();
        testJavaUTF();
        testUTF8Reader();
        testJSONLex();
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

    static String posDesc(Reader.Position pos)
        {
        return $"@{pos.offset} {pos.lineNumber}:{pos.lineOffset}";
        }

    void testUTF8Reader()
        {
        console.println("\n*** testUTF8Reader()");

        InputStream     inRaw  = new ByteArrayInputStream(#./IO.x);
        UTF8Reader      in     = new UTF8Reader(inRaw);
        Boolean         dotdot = False;
        Reader.Position pos    = in.position;
        loop: while (Char ch := in.next())
            {
            if (loop.count <= 20 || inRaw.remaining <= 10)
                {
                console.println($"[{loop.count}] '{ch}' {posDesc(pos)}");
                }
            else if (!dotdot)
                {
                console.println("...");
                dotdot = True;
                }
            pos = in.position;
            }

        console.println($"(eof) position={posDesc(pos)} line={in.lineNumber}");
        }

    void testJSONLex()
        {
        console.println("\n*** testJSONLex()");

        Reader reader = new CharArrayReader(
               `|{
                |   "name" : "Bob",
                |   "age" : 23,
                |   "married" : true,
                |   "parent" : false,
                |   "reason" : null,
                |   "fav_nums" : [ 17, 42 ]
                |   "dog" :
                |      {
                |      "name" : "Spot"
                |      "age" : 7,
                |      }
                |}
                );

        Lexer lexer = new Lexer(reader);
        while (Token tok := lexer.next())
            {
            console.println($"token={tok}");
            }

        console.println($"(eof) position={posDesc(reader.position)}");
        }
    }