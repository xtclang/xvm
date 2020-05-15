module TestSimple
    {
    @Inject Console console;

    void run()
        {
        Class c1 = @Unchecked Int;
        console.println(c1);
        }
    }