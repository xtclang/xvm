module TestLoops.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("Loop tests:");

        testWhile();
        testFor();
        //testDo();
        testLabel();
        //testForEach();
        //testForEachRange();
        }

    void testWhile()
        {
        console.println("\n** testWhile()");

        Int i = 10;
        while (i > 0)
            {
            console.println(i--);
            }
        console.println("We Have Lift-Off!!!");
        }

    void testFor()
        {
        console.println("\n** testFor()");

        for (Int i = 0; i < 10; ++i)
            {
            if (i == 4)
                {
                continue;
                }
            console.println(i);
            }
        }

    void testDo()
        {
        console.println("\n** testDo()");

        Boolean f = false;
        Int j = 0;
        Int i;
        do
            {
            if (j == 4)
                {
                continue;
                }

            i = ++j;

            if (i == 4)
                {
                continue;
                }

            console.println("(in loop) i=" + i + ", j=" + j);
            }
        while (i < 10);

        console.println("(after loop) i=" + i + ", j=" + j);
        }

    void testLabel()
        {
        console.println("\n** testFor()");

        L1: for (Int i = 0; i < 10; ++i)
            {
            console.println(i);
            if (L1.first)
                {
                console.println("frist!");
                }
            }
        }

//    void testForEach()
//        {
//        console.println("\n** testForEach()");
//
//        String[] strs = ["hello", "world"];
//        for (String s : strs)
//            {
//            console.println(s);
//            }
//        }
//
//    void testForEachRange()
//        {
//        console.println("\n** testForEachRange()");
//
//        for (Int i : 1..3)
//            {
//            console.println("i=" + i);
//            }
//        }
    }