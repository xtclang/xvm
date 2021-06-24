module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        }

    void test()
        {
        Boolean flag = false;

        function void (Int) f = i ->
            {
            switch (i)
                {
                case 0:
                    flag = True; // used to fail to compile
                    return;

                default:
                    return;
                }
            };
        }
    }