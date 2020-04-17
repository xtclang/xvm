module TestLoops
    {
    @Inject ecstasy.io.Console console;

    void run()
        {
        console.println("Loop tests:");

        testWhile();
        testFor();
        testLabel();
        testForEachConstRange();
        testForEachSequence();
        //testForEachCollection();
        //testForEachIterator();
        //testDo();
        //testForEachRange();
        }

    void testWhile()
        {
        console.println("\n** testWhile()");

        Int i = 10;
        WhileLabel: while (i > 0)
            {
            console.println(i--);
            if (WhileLabel.first)
                {
                console.println("first!");
                }
            console.println("(count=" + WhileLabel.count + ")");
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

    void testLabel()
        {
        console.println("\n** testLabel()");

        L1: for (Int i = 0; i < 10; ++i)
            {
            console.println(i);
            if (L1.first)
                {
                console.println("first!");
                }
            console.println("(count=" + L1.count + ")");
            }
        }

    void testForEachCollection()
        {
        console.println("\n** testForEachCollection()");

        ecstasy.collections.Collection<String> strs = ["hello", "world"];
        L1: for (String s : strs)
            {
            console.println("s=" + s);
            }
        }

    void testForEachIterator()
        {
        console.println("\n** testForEachIterator()");

        String[] strs = ["hello", "world"];
        L1: for (String s : strs.iterator())
            {
            console.println("s=" + s);
            }
        }

    void testForEachSequence()
        {
        console.println("\n** testForEachSequence()");

        String[] strs = ["hello", "world"];
        L1: for (String s : strs)
            {
            console.println("s=" + s);
            }
        }

    void testForEachConstRange()
        {
        console.println("\n** testForEachConstRange()");

        L1: for (Int i : 9..7)
            {
            console.println("i=" + i + ", first=" + L1.first + ", last=" + L1.last + ", count=" + L1.count);
            }
        }

    void testDo()
        {
        console.println("\n** testDo()");

        Boolean f = false;
        Int j = 0;
        Int i = 0;
        do
            {
            if (j == 4)                 // i is not def asn at this point ...
                {
                continue;               // ... so i is still not def asn at this point
                }

            i = ++j;

            if (i == 4)
                {
                continue;
                }

            console.println("(in loop) i=" + i + ", j=" + j);
            }
        while (i < 10);                 // should be an error here (i is not def asn)

        console.println("(after loop) i=" + i + ", j=" + j);
        }
    }