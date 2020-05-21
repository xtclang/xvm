module TestSimple
    {
    @Inject Console console;

    void run( )
        {
        Class c = String;

        console.println(c.implicitName);
        }
    }