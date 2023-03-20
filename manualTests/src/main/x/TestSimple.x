module TestSimple
    {
    @Inject Console console;

    void run()
        {
        // these now work: the compiler will force a rebuild if anything changes
        Directory dir   = /resources;
        File      file1 = ./annos.x;

        // the following currently doesn't work - no automatic rebuild upon a change:

        // - need to change FSNodeConstant, so the compiler knows the source
        File file2 = ./webTests/Curl.x;

        // - need to change the LiteralConstant to hold the "source" for this to work
        Byte[] bytes  = #./webTests/resources/hello/index.html;
        String string = $./webTests/resources/hello/index.html;
        }
    }