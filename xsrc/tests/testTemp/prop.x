module TestProps.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("hello world! (property tests)");

        testMethodProperty();
        }

    void testMethodProperty()
        {
        console.println("\n** testMethodProperty()");

        for (Int i : 1..3)
            {
            showMethodProperty();
            }
        }

    void showMethodProperty()
        {
        private Int x = 0;
        // compiles as:
        // private Int x;       // not inside the method compilation itself
        // x = 0;               // THIS CODE gets compiled as part of the method (but within an "if (!(&x.assigned))" check

        console.println(" - in showMethodProperty(), ++x=" + ++x);
        }
    }