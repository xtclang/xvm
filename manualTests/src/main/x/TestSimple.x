module TestSimple.test.org
    {
    import ecstasy.fs.FileNode;

    @Inject Console console;

    void run()
        {
        @Inject Directory curDir;

        Set<File|Directory> files = new HashSet(); // this used to throw
        for (String name : curDir.names())
            {
            assert File|Directory node := curDir.find(name);
            files.add(node);
            }

        console.println(files.appendTo(new StringBuffer(), "\n", "", "").toString());
        }
    }