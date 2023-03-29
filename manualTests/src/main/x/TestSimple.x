module TestSimple
    {
    @Inject Console console;

    void run()
        {
        String? x = bar();
        String? y = baz();
        console.print($"before: {x=}, {y=}");
        x = y?;
        console.print($"after: {x=}");
        }

    String? bar() = "hello";
    String? baz() = Null;
    }