module IO
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
        loop: while (!in.eof)
            {
            console.println($"[{loop.count}] {in.readByte()}");
            }
        console.println("(eof)");
        }
    }
