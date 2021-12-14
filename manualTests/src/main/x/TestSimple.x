module TestSimple.test.org
    {
    import ecstasy.fs.FileNode;

    @Inject Console console;

    void run()
        {
        @Inject Directory curDir;

        Map<FileNode, String> files = new HashMap();
        for (String name : curDir.names())
            {
            assert File|Directory node := curDir.find(name);
            files.put(node, name);
            }

        console.println(files.keys.appendTo(new StringBuffer(), "\n", "", "").toString());

        for (String name : curDir.names())
            {
            assert File|Directory node := curDir.find(name);
            assert files.contains(node);
            }
        }
    }