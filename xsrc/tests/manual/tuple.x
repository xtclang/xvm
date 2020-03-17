module TestTuples.xqiz.it
    {
    @Inject ecstasy.io.Console console;

    void run()
        {
        console.println("Tuple tests:");

        testSimple();
        testConv();
        testConstElement();
        testConstSlice();
        testMultiAssign();
        }

    void testSimple()
        {
        console.println("\n** testSimple()");

        Tuple<String, String, Int> t = ("hello", "world", 17);
        console.println($"t={t}");
        for (Int i = 0; i < 3; ++i)
            {
            console.println($"tuple[{i}]={t[i]}");
            }

        String s0 = t[0];
        String s1 = t[1];
        Int    i2 = t[2];
        console.println($"fields: {s0}, {s1}, {i2}");

        Tuple<String, Map<Int, String>> t2 = ("goodbye", Map:[4="now"]);
        console.println($"fields: {t2[0]}, {t2[1]}");
        }

    void testConv()
        {
        console.println("\n** testConv()");

        Tuple<String, IntLiteral> t1 = getTupleSI();
        console.println("t1 = " + t1);

        // TODO: should the following compile?
        // Tuple<String, Int> t2 = getTupleSI();

        private static Tuple<String, IntLiteral> getTupleSI()
            {
            return ("Hello", 4);
            }
        }

    void testConstElement()
        {
        console.println("\n** testConstElement()");

        String blind = (3, "blind", "mice", "!") [1];
        console.println("tuple(1)=" + blind);

        Int num = (3, "blind", "mice", "!") [0];
        console.println("tuple(0)=" + num);
        }

    void testConstSlice()
        {
        console.println("\n** testConstSlice()");

        Tuple<Int, String> blind = (3, "blind", "mice", "!") [0..1];
        console.println("tuple[0..1]=" + blind);

        Tuple<String, Int> blind2 = (3, "blind", "mice", "!") [1..0];
        console.println("tuple[1..0]=" + blind2);
        }

    void testMultiAssign()
        {
        console.println("\n** testMultiAssign()");
        (String s, Int i) = ("hello", 3);
        console.println("s=" + s + ", i=" + i);
        }
    }