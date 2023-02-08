module TestSimple
    {
    @Inject Console console;

    void run()
        {
        @Inject Directory tmpDir;

        File file = tmpDir.fileFor("not.there");

        console.print(file.read(0..2));
        }
    }