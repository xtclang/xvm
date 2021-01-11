module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        @Inject Directory curDir;

        File fileXtc;
        if (fileXtc := curDir.findFile("test"))
            {
            }
        else
            {
            console.println("not found");
            return;
            }
        console.println(fileXtc);
        }
    }