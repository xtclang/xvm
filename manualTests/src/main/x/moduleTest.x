module moduleTest
    {
    @Inject Console console;
    void run()
        {
        console.println($"in {this:module}");
        new somePkg.someClz<Int>();
        }
    }