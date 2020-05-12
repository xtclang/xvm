module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Type t = immutable Map<Int>;
        console.println(t);
        }
    }