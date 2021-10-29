module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        new Test(); // "ignored" result used to blow up
        }

    service Test
        {
        }
    }