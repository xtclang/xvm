module TestSimple
    {
    @Inject Console console;

    void run()
        {
        var cmp = (String x, String y) -> x <=> y;  // used to fail to compile

        console.println(&cmp.actualType);
        }
    }