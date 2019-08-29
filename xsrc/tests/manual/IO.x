module TestIO
    {
    import Ecstasy.io.ByteArrayInputStream;
    import Ecstasy.io.DataInputStream;
    import Ecstasy.io.JavaDataInput;

    @Inject Console console;

    void run()
        {
        console.println("*** IO tests ***");

        testInputStream();
        testJavaUTF();
        }

    void testInputStream()
        {
        console.println("testInputStream()");
        File    file   = ./IO.x;
        Byte[]  raw    = file.contents;
        var     in     = new ByteArrayInputStream(raw);
        Boolean dotdot = False;
        loop: while (!in.eof)
            {
            Byte b = in.readByte();
            if (loop.count <= 8 || in.remaining <= 2)
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
        var in = new @JavaDataInput ByteArrayInputStream([0x00, 0x03, 0x43, 0x41, 0x4e]);
        console.println($"string={in.readString()}");
        }
    }