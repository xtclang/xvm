module TestProps.xqiz.it
    {
    @Inject X.io.Console console;

    void run()
        {
        console.println("hello world! (property tests)");

        testMethodProperty();
        testLazyProperty();
        testDelegation();
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
        // x = 0;               // THIS CODE gets compiled as part of the method
                                // (but within an "if (!(&x.assigned))" check

        static Int y = calcStaticProperty();
        // compiles as a private static property, which should be initialized just once
        // (before the method is called the very first time)

        console.println($" - in showMethodProperty(), ++x={++x}, y={y}");
        }

    static Int calcStaticProperty()
        {
        @Inject X.io.Console console;

        console.println(" - in calcStaticProperty()");
        return 3;
        }

    void testLazyProperty()
        {
        console.println("\n** testLazyProperty()");

        console.println(" lazy=" + lazy);
        }

    @Lazy Int lazy.calc()
        {
        console.println(" - in lazy.calc() " + toString());
        return 42;
        }

    void testDelegation()
        {
        console.println("\n** testDelegation()");

        class NamedNumber(String name, Int number)
                delegates Stringable(name)
            {
            }

        class NamedNumber2(String name, Int number)
                delegates Stringable-Object(name)
            {
            }

        NamedNumber nn = new NamedNumber("answer", 42);
        console.println($"nn.estimateStringLength()={nn.estimateStringLength()}");
        console.println($"nn.toString()={nn.toString()}");

        NamedNumber2 nn2 = new NamedNumber2("answer", 42);
        console.println($"nn2.estimateStringLength()={nn2.estimateStringLength()}");
        console.println($"nn2.toString()={nn2.toString()}");
        }
    }