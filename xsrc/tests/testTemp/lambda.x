module TestLambda.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("Lambda tests:");

        // testVoid();
        testBasic();
        testEffectivelyFinalCapture();
        testThisCapture();
        testRefCapture();
        testVarCapture();
        testComplexCapture();
        }

// TODO
//    void testVoid()
//        {
//        console.println("\n** testBasic()");
//
//        function void() f = () -> console.println("in the lambda!");
//
//        f();
//        }

    void testBasic()
        {
        console.println("\n** testBasic()");

        function Int() f = () -> 4;
        console.println("result=" + f());
        }

    // test with params
    // test

    void testEffectivelyFinalCapture()
        {
        console.println("\n** testEffectivelyFinalCapture()");

        Int i = 4;
        function Int() f = () -> i;
        console.println("result=" + f());
        }

    void testThisCapture()
        {
        console.println("\n** testThisCapture()");

        function String() f = () -> foo();
        console.println("result=" + f());
        }
    String foo()
        {
        return "hello";
        }

    void testRefCapture()
        {
        console.println("\n** testRefCapture()");

        Int i = 0;
        do
            {
            function Int() f = () -> i;
            console.println("result=" + f());
            }
        while (i++ < 5);
        }

    void testVarCapture()
        {
        console.println("\n** testVarCapture()");

        Int i = 0;
        while (i < 5)
            {
            function Int() f = () -> ++i;
            console.println("result=" + f());
            // console.println("i=" + i + ", result=" + f() + ", i=" + i);
            }
        }

    void testComplexCapture()
        {
        console.println("\n** testComplexCapture()");

        Int i = 0;
        while (i < 5)
            {
            function Int() f1 = () -> i;
            console.println("result=" + f1());    // initially would appear to be "Int", but must be "Ref<Int>"
            function Int() f2 = () -> ++i;
            console.println("result=" + f2());
            }
        }
    }