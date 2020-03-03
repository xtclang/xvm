module TestSimple.xqiz.it
    {
    import Ecstasy.collections.HashMap;

    @Inject Ecstasy.io.Console console;

    void run()
        {
        @Inject Directory curDir;

        console.println(curDir);
        assert !curDir.find("NO_SUCH_FILE");
        }
    }
