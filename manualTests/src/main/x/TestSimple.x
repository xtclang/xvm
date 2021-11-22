module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        }

    void testProcess(Map<Int, Int> map)
        {
        Int count = 0;

        map.process^(0, e ->
            {
            @Inject Timer timer;
            timer.schedule(Duration:1s, () ->
                {
                TODO
                });

            return ++count; // this used to fail the compilation
            });
        }
    }
