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

        // testInterval();
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
        Object c = a..b;
        // Range<Int> c = a..b;
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

        // TODO GG - need "IntLiteral" support for:
        //
        //    Object o = 4.to<Int>();
        //
        // java.lang.UnsupportedOperationException: TODO: IntLiteral{value="4"}
        //   	at org.xvm.runtime.ObjectHeap.getConstType(ObjectHeap.java:110)
        //   	at org.xvm.runtime.ObjectHeap.createConstHandle(ObjectHeap.java:63)

        Int    i = 42;
        Object o = i;
        console.println("o=" + o);
        Int    n = o.as(Int);
        console.println("n=" + n);
        }
    }