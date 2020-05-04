module TestSimple
    {
    @Inject Console console;

    void run()
        {
        import ecstasy.collections.HashMap;

        console.println(console);
        console.println(&console.actualType);

        if (Const actual := &console.revealAs(Const))
            {
            assert;
            }
        }
    }