module TestSimple
    {
    @Inject Clock clock;
    @Inject Console console;

    void run()
        {
        console.println(clock); // used to produce a "_native" name
        }
    }