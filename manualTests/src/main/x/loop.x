module TestLoops
    {
    @Inject Console console;

    void run()
        {
        console.print("Loop tests:");

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
        console.print("\n** testWhile()");

        Int i = 10;
        WhileLabel: while (i > 0)
            {
            console.print(i--);
            if (WhileLabel.first)
                {
                console.print("first!");
                }
            console.print("(count=" + WhileLabel.count + ")");
            }
        console.print("We Have Lift-Off!!!");
        }

    void testFor()
        {
        console.print("\n** testFor()");

        for (Int i = 0; i < 10; ++i)
            {
            if (i == 4)
                {
                continue;
                }
            console.print(i);
            }
        }

    void testLabel()
        {
        console.print("\n** testLabel()");

        L1: for (Int i = 0; i < 10; ++i)
            {
            console.print(i);
            if (L1.first)
                {
                console.print("first!");
                }
            console.print("(count=" + L1.count + ")");
            }
        }

    void testForEachCollection()
        {
        console.print("\n** testForEachCollection()");

        Collection<String> strs = ["hello", "world"];
        L1: for (String s : strs)
            {
            console.print("s=" + s);
            }
        }

    void testForEachIterator()
        {
        console.print("\n** testForEachIterator()");

        String[] strs = ["hello", "world"];
        L1: for (String s : strs.iterator())
            {
            console.print("s=" + s);
            }
        }

    void testForEachSequence()
        {
        console.print("\n** testForEachSequence()");

        String[] strs = ["hello", "world"];
        L1: for (String s : strs)
            {
            console.print("s=" + s);
            }
        }

    void testForEachConstRange()
        {
        console.print("\n** testForEachConstRange()");

        L1: for (Int i : 9..7)
            {
            console.print("i=" + i + ", first=" + L1.first + ", last=" + L1.last + ", count=" + L1.count);
            }
        }

    void testDo()
        {
        console.print("\n** testDo()");

        Boolean f = False;
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

            console.print("(in loop) i=" + i + ", j=" + j);
            }
        while (i < 10);                 // should be an error here (i is not def asn)

        console.print("(after loop) i=" + i + ", j=" + j);
        }
    }