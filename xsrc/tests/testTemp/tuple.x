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

        Tuple<String, String, Int> t = ("hello", "world", 17);
        console.println("t="+t);
        for (Int i = 0; i < 3; ++i)
            {
            console.println("tuple[" + i + "]=" + t[i]);
            }

        String s0 = t[0];
        String s1 = t[1];
        Int    i2 = t[2];
        console.println("fields: " + s0 + ", " + s1 + ", " + i2);
        }
    }