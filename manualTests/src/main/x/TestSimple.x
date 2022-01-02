module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Int i = 3;
        console.println(i.hashCode());
        }
    }