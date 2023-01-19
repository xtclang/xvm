module moduleTest
    {
    @Inject Console console;
    void run()
        {
        console.print($"in {this:module}");
        new somePkg.someClz<Int>();
        }
    }