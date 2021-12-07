module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        for (Int i : 0..25)
            {
            TestService test = new TestService(i);
            }
        wait(Duration:2s);
        }

    service TestService
        {
        construct(Int i)
            {
            console.println(i);
            }
        }

    void wait(Duration duration)
        {
        @Inject Timer timer;

        @Future Tuple<> result;
        timer.schedule(duration, () ->
            {
            assert:debug; // VF command should show just one TestService instance
            result = Tuple:();
            });
        return result;
        }

    }