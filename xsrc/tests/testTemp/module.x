module TestTemp.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("hello world!");

        testInts();
        testBools();
        testInterval();
        }

    void testInts()
        {
        Int a = 6;
        Int b = 1;
        Int c = a + b;

        console.println("a=" + a);
        console.println("b=" + b);
        console.println("c=" + c);
        }

    void testBools()
        {
        Boolean a = true;
        Boolean b = false;

//      Boolean c = true == false;
//        Boolean c = a & b;
//
        console.println("a=" + a);
        console.println("b=" + b);
//        console.println("c=" + c);
        }

    void testInterval()
        {
        Int a = 2;
        Int b = 5;
        Object c = a..b;
        // Range<Int> c = a..b;
        console.println("range=" + c);
        }
    }