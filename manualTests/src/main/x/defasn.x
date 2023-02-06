module TestDefAsn
    {
    @Inject ecstasy.io.Console console;

    void run()
        {
        testDefAssignment();
        testShort();
        testNameHiding();
        testWhileLoop(0);
        testForLoop(0);
        }

    Boolean gimmeTrue()
        {
        return True;
        }
    String? maybeNull()
        {
        return Null;
        }

    void testDefAssignment()
        {
        console.print("\n** testDefAssignment()");

        static (String, Int) name()
            {
            return "hello", 5;
            }

        String n;
        Int    c;
        (n, c) = name();

        assert n.size == c;

        Int i;
        Boolean f1 = gimmeTrue();
        Boolean f2 = gimmeTrue();

        // vary this test as necessary (do vs. while; break vs. continue; && vs. ||, etc.)
        L1: do
            {
            if (f1 && {i=3; return True;})
                {
                //i = 3;
                break L1;
                }

            i = 1;
            continue L1;
            }
        while (f2);

        console.print("i=" + i);
        }

    void testShort()
        {
        console.print("\n** testShort()");

        Int i;
        Boolean f1 = gimmeTrue();
        Boolean f2 = gimmeTrue();
        String? s  = maybeNull();

        if (s?.size > 1 && {i=3; return True;})
            {
            //i = 3;
            }
        else
            {
            i = 4;
            }

        console.print("i=" + i);
        }

    String name = "top-level property";

    void testNameHiding()
        {
        static conditional (String, Int) name()
            {
            return True, "hello", 5;
            }

        if ((String name, Int count) := name())
            {
            assert name.size == count;
            }
        }

    void testWhileLoop(Int? in)
        {
        Int i;
        while (True)
            {
            if (in.is(Int))
                {
                i = in;
                break;
                }
            else
                {
                throw new IllegalState();
                }
            }

        assert i >= 0;
        }

    void testForLoop(Int? in)
        {
        Int i;
        for (;;)
            {
            if (in.is(Int))
                {
                i = in;
                break;
                }
            else
                {
                return;
                }
            }

        assert i >= 0;
        }
    }