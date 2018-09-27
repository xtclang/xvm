module TestTuples.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("Tuple tests:");

        testSimple();
        }

    void testSimple()
        {
        console.println("\n** testSimple()");

        Tuple t = ("hello", "world", '!', 17);
        //for (Int i : 0..3)
        for (Int i = 0; i < 4; ++i)
            {
            console.println("i="+i);
            // console.println("tuple[" + i + "]=" + t[i]);
            }
        }
    }