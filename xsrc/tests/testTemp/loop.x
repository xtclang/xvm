module TestLoops.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("Loop tests:");

        testWhile();
        testFor();
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
            console.println(i);
            }
        }
    }