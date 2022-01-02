module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        console.println("foo".hashCode());
        }
    }