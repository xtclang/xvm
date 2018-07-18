module TestLambda.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("Lambda tests:");

        testBasic();
        testEffectivelyFinalCapture();
        testThisCapture();
        testRefCapture();
        testVarCapture();
        }

    void testBasic()
        {
        console.println("\n** testBasic()");

        System.out.println("result=" + () -> 4);
        }

    // test with params
    // test

    void testEffectivelyFinalCapture()
        {
        console.println("\n** testEffectivelyFinalCapture()");

        Int i = 4;
        System.out.println("result=" + () -> i);
        }

    void testThisCapture()
        {
        console.println("\n** testThisCapture()");

        System.out.println("result=" + () -> foo());
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
            System.out.println("result=" + () -> i);
            }
        while (i++ < 5);
        }

    void testVarCapture()
        {
        console.println("\n** testVarCapture()");

        Int i = 0;
        while (i < 5)
            {
            System.out.println("result=" + () -> ++i);
            }
        }
    }