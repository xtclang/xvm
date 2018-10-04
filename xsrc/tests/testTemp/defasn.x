module TestDefAsn.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("hello world!");

        testDefAssignment();
        }

    void testDefAssignment()
        {
        console.println("\n** testDefAssignment()");

        Int i;
        Boolean f1 = false;
        Boolean f2 = false;

        L1: if (f2)
            {
            if (f1)
                {
                i = 3;
                break L1;
                }

            i = 1;
            }
        else
            {
            i = 2;
            }

        console.println("i=" + i);
        }
    }