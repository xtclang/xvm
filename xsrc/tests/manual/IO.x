module TestIO
    {
    import Ecstasy.io.ByteArrayInputStream;
    import Ecstasy.io.DataInputStream;
    import Ecstasy.io.InputStream;
    import Ecstasy.io.JavaDataInput;
    import Ecstasy.io.UTF8Reader;

    @Inject Console console;

    void run()
        {
        testInputStream();
        testJavaUTF();
        testUTF8Reader();
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

        InputStream inRaw  = new ByteArrayInputStream(#./IO.x);
        UTF8Reader  in     = new UTF8Reader(inRaw);
        Boolean     dotdot = False;
        loop: while (Char ch := in.next())
            {
            if (loop.count <= 12 || inRaw.remaining <= 2)
                {
                console.println($"[{loop.count}] '{ch}'");
                }
            else if (!dotdot)
                {
                console.println("...");
                dotdot = True;
                }
            }
        console.println("(eof)");
        }
    }