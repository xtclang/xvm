module TestDefAsn.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        testDefAssignment();
        }

    void testDefAssignment()
        {
        console.println("\n** testDefAssignment()");

        Int i;
        Boolean f1 = true;
        Boolean f2 = true;

        L1: do
            {
            if (f1 && {i=3; return true;})
                {
                //i = 3;
                break L1;
                }

            i = 1;
            continue L1;
            }
            while (f2);

        console.println("i=" + i);
        }
    }