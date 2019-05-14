module TestFiles.xqiz.it
    {
    import X.fs.Directory;
    import X.fs.Path;
    import X.fs.FileStore;

    @Inject Console console;

    void run()
        {
        testPaths();
        testInject();
        }

    void testPaths()
        {
        console.println("\n** testPaths()");
        console.println("root=" + Path.ROOT);
        console.println("parent=" + Path.PARENT);
        console.println("current=" + Path.CURRENT);

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

    void testInject()
        {
        console.println("\n** testInject()");

        @Inject FileStore storage;

        console.println("readOnly="  + storage.readOnly);
        console.println("capacity="  + storage.capacity);
        console.println("bytesFree=" + storage.bytesFree);
        console.println("bytesUsed=" + storage.bytesUsed);

        @Inject Directory rootDir;
        console.println("rootDir=" + rootDir + " created " + rootDir.created);

        @Inject Directory homeDir;
        console.println("homeDir=" + homeDir + " modified " + homeDir.modified);

        @Inject Directory curDir;
        console.println("curDir=" + curDir + " modified " + curDir.modified);

        @Inject Directory tmpDir;
        console.println("tmpDir=" + tmpDir + " accessed " + tmpDir.accessed);
        }
    }