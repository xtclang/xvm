module TestFiles.xqiz.it
    {
    import X.fs.Path;

    @Inject Console console;

    void run()
        {
        console.println("*** file tests ***\n");

        testPaths();
        }

    void testPaths()
        {
        Path path = new Path(null, "test");
        console.println("path=" + path);

        path = new Path(path, "sub");
        console.println("path=" + path);

        path = new Path(path, "more");
        console.println("path=" + path);

        for (Int i : 0..2)
            {
            console.println("path[" + i + "]=" + path[i]);
            }

        console.println("path[1..2]=" + path[1..2]);
        console.println("path[0..1]=" + path[0..1]);
        console.println("path[2..0]=" + path[2..0]);

        path = ROOT + path;
        console.println("path=" + path);
        }
    }