module TestTemp.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("hello world!");

        testBools();
        testInts();
        testIsA();
        testCast();
        testTernary();
        testSpaceship();
        testElvis();
        testLoop();
        testElseExpr();

        // REVIEW GG
        // testInterval();
        // testTupleConv();
        // testMap();
        }

    void testInts()
        {
        console.println("\n** testInts()");

        Int a0 = -1217;
        Int a = -a0;
        Int b = 3;
        console.println("a=" + a);
        console.println("b=" + b);

        console.println("a + b = "   + (a + b));
        console.println("a - b = "   + (a - b));
        console.println("a * b = "   + (a * b));
        console.println("a / b = "   + (a / b));
        console.println("a % b = "   + (a % b));
        console.println("a & b = "   + (a & b));
        console.println("a | b = "   + (a | b));
        console.println("a ^ b = "   + (a ^ b));
        console.println("a << b = "  + (a << b));
        console.println("a >> b = "  + (a >> b));
        console.println("a >>> b = " + (a >>> b));

        console.println("\n** pre/post inc/dec");
        console.println("a   = " + a);
        console.println("a++ = " + a++);
        console.println("a   = " + a);
        console.println("++a = " + ++a);
        console.println("a   = " + a);
        console.println("a-- = " + a--);
        console.println("a   = " + a);
        console.println("--a = " + --a);
        console.println("a   = " + a);
        }

    void testBools()
        {
        console.println("\n** testBools()");

        console.println("!true=" + !true);
        console.println("!false=" + !false);

        Boolean a = true;
        Boolean b = false;
        console.println("a=" + a);
        console.println("b=" + b);
        console.println("!a=" + !a);
        console.println("!b=" + !b);
        console.println("~a=" + ~a);
        console.println("~b=" + ~b);
        }

    void testInterval()
        {
        console.println("\n** testInterval()");

        Int a = 2;
        Int b = 5;
        // Object c = a..b;
        Range<Int> c = a..b;
        console.println("range=" + c);
        }

    void testArrays()
        {
        console.println("\n** testArrays()");

        // ArrayList
        Int[] list = new Int[]; // Array<Int> list = new Array<Int>();

        Int[] array = new Int[10]; // just like Java
        }

    void testIsA()
        {
        console.println("\n** testIsA()");

        console.println("\n(testing a string in a variable of type object)");
        Object o = "hello";
        console.println("o           =" + o);
        console.println("o.is(Object)=" + o.is(Object));
        console.println("o.is(String)=" + o.is(String));
        console.println("o.is(Int)   =" + o.is(Int));

        console.println("\n(testing an int in a variable of type int)");
        Int i = 5;
        if (i.is(Object))
            {
            console.println("i.is(Object) true (good)");
            }
        else
            {
            console.println("i.is(Object) false (bad)");
            }

        if (i.is(Int))
            {
            console.println("i.is(Int) true (good)");
            }
        else
            {
            console.println("i.is(Int) false (bad)");
            }

        if (i.is(String))
            {
            console.println("i.is(String) true (bad)");
            }
        else
            {
            console.println("i.is(String) false (good)");
            }
        }

    void testCast()
        {
        console.println("\n** testCast()");

        Int    i = 42;
        Object o = i;
        console.println("o=" + o);
        Int    n = o.as(Int);
        console.println("n=" + n);

        Object o2 = 4.to<Int>();
        console.println("o2=" + o2);
        }

    void testTernary()
        {
        console.println("\n** testTernary()");

        Int i = 42;
        console.println("i=" + i);
        console.println("i%2=" + (i % 2));

        console.println("i>40?greater:not -> " + (i > 40 ? "greater" : "not greater"));
        console.println("i%2==0?even:odd  -> " + (i % 2 == 0 ? "even" : "odd"));
        }

    void testSpaceship()
        {
        console.println("\n** testSpaceship()");

        Int a = 42;
        Int b = 45;
        console.println("a=" + a + ", b=" + b + ", a<=>b=" + (a <=> b));
        }

    void testTupleConv()
        {
        console.println("\n** testTupleConv()");

        Tuple<String, IntLiteral> t1 = getTupleSI();
        console.println("t1 = " + t1);

        Tuple<String, Int> t2 = getTupleSI();
        }

    Tuple<String, IntLiteral> getTupleSI()
        {
        return ("Hello", 4);
        }

    void testElvis()
        {
        console.println("\n** testElvis()");

        Int a = 42;
        Int b = 45;
        // this is an error: console.println("a=" + a + ", b=" + b + ", a?:b=" + (a ?: b));

        Int? c = a;
        console.println("c=" + c + ", b=" + b + ", c?:b=" + (c ?: b));

        c = null;
        console.println("c=" + c + ", b=" + b + ", c?:b=" + (c ?: b));
        }

    void testElseExpr()
        {
        console.println("\n** testElseExpr()");

        IntLiteral? a = null;
        Int b = 7;
        console.println("a=" + a + ", b=" + b + ", a?.to<Int>():b=" + (a?.to<Int>():b));

        a = 4;
        console.println("a=" + a + ", b=" + b + ", a?.to<Int>():b=" + (a?.to<Int>():b));
        }

    void testMap()
        {
        console.println("\n** testMap()");

        console.println("Map:{1=one, 2=two}=" + Map:{1="one", 2="two"});
        }

    void testLoop()
        {
        console.println("\n** testLoop()");

        Int i = 10;
        while (i > 0)
            {
            console.println(i--);
            }
        console.println("We Have Lift-Off!!!");
        }
    }