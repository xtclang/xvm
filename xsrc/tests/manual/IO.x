module TestIO
    {
    import Ecstasy.io.ByteArrayInputStream;

    @Inject Console console;

    void run()
        {
        console.println("*** IO tests ***");

        testInputStream();
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
    }