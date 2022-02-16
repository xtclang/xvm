module TestProps
    {
    @Inject Console console;
    @Inject Clock   clock;
    @Inject Timer   timer;

    void run()
        {
        testStandardProperty();
        testMethodProperty();
        testLazyProperty();
        testModuleProperty();
        testDelegation();
        testAccess();
        }

    class Standard(Int x);

    void testStandardProperty()
        {
        Standard s = new Standard(1);
        Int iterations = 1_000_000;
        timer.reset();
        for (Int i = 0; i < iterations; ++i)
            {
            }
        Duration timeBase = timer.elapsed;
        timer.reset();
        for (Int i = 0; i < iterations; ++i)
            {
            s.x += i; // 1
            s.x += i; // 2
            s.x += i; // 3
            s.x += i; // 4
            s.x += i; // 5
            s.x += i; // 6
            s.x += i; // 7
            s.x += i; // 8
            s.x += i; // 9
            s.x += i; // 10
            }
        Duration time = timer.elapsed;
        console.println($"get/set property latency {((time - timeBase) / (iterations * 10)).nanoseconds} ns");
        }

    void testMethodProperty()
        {
        console.println("\n** testMethodProperty()");

        TestProperty test = new TestProperty();
        for (Int i : 1..3)
            {
            test.showMethodProperty();
            }
        }

    class TestProperty
        {
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
        }

    static Int calcStaticProperty()
        {
        @Inject ecstasy.io.Console console;

        console.println(" - in calcStaticProperty()");
        return 3;
        }

    void testLazyProperty()
        {
        console.println("\n** testLazyProperty()");

        console.println("lazy=" + lazy);
        }

    static void testModuleProperty()
        {
        TestProps.console.println("\n** testModuleProperty()");
        TestProps.console.println("now=" + this:module.clock.now);
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

    void testAccess()
        {
        Derived d = new Derived();
        d.report();

        for (Int i : 0..5)
            {
            val x = d.p4;
            }
        d.&p4.report();

        class Base
            {
            private Int p1 = 1;

            private Int p2()
                {
                return 2;
                }

            private Int p3()
                {
                return 3;
                }

            Int p4
                {
                @Override
                Int get()
                    {
                    ++count;
                    return super();
                    }

                void report()
                    {
                    console.println($"Property p4 was called {count} times!");
                    }

                private Int count;
                }

            void report()
                {
                console.println($"Base   : p1={p1},    p2()={p2()}, p3()={p3()}");
                }
            }

        class Derived
                extends Base
            {
            Int p1()
                {
                return 11;
                }

            Int p2 = 22;

            Int p3()
                {
                return 33;
                }

            @Override
            Int p4
                {
                @Override
                void report()
                    {
                    super();
                    console.println($"Method foo() was called {++count1} times");
                    }

                private Int count1; // TODO GG: using the same name (count) asserts at RT
                }

            @Override
            void report()
                {
                super();

                console.println($"Derived: p1()={p1()}, p2={p2},  p3()={p3()}");
                }
            }
        }
    }