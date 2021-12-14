module TestSimple.test.org
    {
    import ecstasy.fs.FileNode;

    @Inject Console console;

    void run()
        {
        @Inject Directory curDir;

        Map<File|Directory> files = new HashMap();
        for (String name : curDir.names())
            {
            assert File|Directory node := curDir.find(name);
            files.put(node, name); // this used to assert
            }
        }
    }