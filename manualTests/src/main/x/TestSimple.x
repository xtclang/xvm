module TestSimple.test.org
            delegates Stringable(NAME)
    {
    @Inject Console console;

    void run()
        {
        String name = NAME;
        console.println(name);
        console.println(this);
        }

    static String NAME = foo();

    static String foo()
        {
        return "Simple";
        }
    }