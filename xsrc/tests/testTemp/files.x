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

        path = ROOT + path;
        console.println("path=" + path);
        }
    }