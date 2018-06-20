module TestTemp.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("hello world!");

        testInts();
        testBools();
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
        }

    void testBools()
        {
        console.println("\n** testBools()");

        Boolean a = true;
        Boolean b = false;
        Boolean c = true == false;
//        Boolean c = a & b;
//
        console.println("a=" + a);
        console.println("b=" + b);
        console.println("c=" + c);
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
    }