module TestSimple
    {
    @Inject ecstasy.io.Console console;

    void run()
        {
        @Inject Clock clock;

        console.println("The time is " + clock.now);
        }
    }