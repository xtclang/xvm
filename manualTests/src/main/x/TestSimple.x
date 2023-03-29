module TestSimple
    {
    @Inject Console console;

    void run()
        {
        console.print($"false={foo(False)? : "?"}, true={foo(True)? : "?"}");
        }

    conditional String foo(Boolean f)
        {
        return f, "hello";
        }
    }