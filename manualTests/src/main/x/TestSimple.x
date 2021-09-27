module TestSimple.test.org
    {
    @Inject Console console;

    void run()
        {
        Int tx = 0;

        while (tx < 4)
            {
            for (Int i : [1 .. 2])
                {
                tx++;
                }

            console.println(tx); // this used to be an illegal assign warning
            }
        }
    }