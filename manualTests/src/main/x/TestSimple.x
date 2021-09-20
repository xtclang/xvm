module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        console.println("Started");
        wait(Duration.ofSeconds(4));
        console.println("Finished");
        }

    void wait(Duration duration)
        {
        @Inject Timer timer;

        @Future Tuple<> result;
        timer.schedule(duration, () ->
            {
            if (!&result.assigned)
                {
                console.println("Shutting down the test");
                result=Tuple:();
                }
            });
        return result;
        }
    }