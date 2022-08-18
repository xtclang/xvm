module TestSimple
    {
    @Inject Clock clock;
    @Inject Console console;

    void run()
        {
        console.println(clock);
        console.println(console); // used to blow up at run-time
        }
    }