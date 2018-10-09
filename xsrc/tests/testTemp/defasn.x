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
        Boolean f1 = true;
        Boolean f2 = true;

        L1: while (f2)
            {
            if (f1 && {i=3; return true;})
                {
                //i = 3;
                break L1;
                }

            i = 1;
            continue L1;
            }
//        else
//            {
//            i = 2;
//            }

        console.println("i=" + i);
        }
    }