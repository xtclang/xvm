module TestSimple
    {
    @Inject Console console;

    void run()
        {
        }

    class Test
            extends Test
        {
        }

    class Test1
            extends Test2
        {
        }

    class Test2
            extends Test1
        {
        }
    }
