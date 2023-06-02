module TestProps {
    @Inject Console console;
    @Inject Clock   clock;
    @Inject Timer   timer;

    void run() {
        testStandardProperty();
        testMethodProperty();
        testLazyProperty();
        testModuleProperty();
        testDelegation();
        testAccess();
    }

    class Standard(Int x);

    void testStandardProperty() {
        Standard s = new Standard(1);
        Int iterations = 100_000;
        timer.reset();
        for (Int i = 0; i < iterations; ++i) {}
        Duration timeBase = timer.elapsed;
        timer.reset();
        for (Int i = 0; i < iterations; ++i) {
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
        console.print($"get/set property latency {((time - timeBase) / (iterations * 10)).nanoseconds} ns");
    }

    void testMethodProperty() {
        console.print("\n** testMethodProperty()");

        TestProperty test = new TestProperty();
        for (Int i : 1..3) {
            test.showMethodProperty();
        }
    }

    class TestProperty {
        void showMethodProperty() {
            private Int x = 0;
            // compiles as:
            // private Int x;       // not inside the method compilation itself
            // x = 0;               // THIS CODE gets compiled as part of the method
                                    // (but within an "if (!(&x.assigned))" check

            static Int y = calcStaticProperty();
            // compiles as a private static property, which should be initialized just once
            // (before the method is called the very first time)

            console.print($" - in showMethodProperty(), ++x={++x}, y={y}");
        }
    }

    static Int calcStaticProperty() {
        @Inject ecstasy.io.Console console;

        console.print(" - in calcStaticProperty()");
        return 3;
    }

    void testLazyProperty() {
        console.print("\n** testLazyProperty()");

        console.print("lazy=" + lazy);
    }

    static void testModuleProperty() {
        TestProps.console.print("\n** testModuleProperty()");
        TestProps.console.print("now=" + this:module.clock.now);
    }

    @Lazy Int lazy.calc() {
        console.print(" - in lazy.calc() " + toString());
        return 42;
    }

    void testDelegation() {
        console.print("\n** testDelegation()");

        class NamedNumber(String name, Int number)
                delegates Stringable(name) {}

        class NamedNumber2(String name, Int number)
                delegates Stringable-Object(name) {}

        NamedNumber nn = new NamedNumber("answer", 42);
        console.print($"nn.estimateStringLength()={nn.estimateStringLength()}");
        console.print($"nn.toString()={nn.toString()}");

        NamedNumber2 nn2 = new NamedNumber2("answer", 42);
        console.print($"nn2.estimateStringLength()={nn2.estimateStringLength()}");
        console.print($"nn2.toString()={nn2.toString()}");
    }

    void testAccess() {
        Derived d = new Derived();
        d.report();

        for (Int i : 0..5) {
            val x = d.p4;
        }
        d.&p4.report();

        class Base {
            private Int p1 = 1;

            private Int p2() {
                return 2;
            }

            private Int p3() {
                return 3;
            }

            Int p4 {
                @Override
                Int get() {
                    ++count;
                    return super();
                }

                void report() {
                    console.print($"Property p4 was called {count} times!");
                }

                private Int count;
            }

            void report() {
                console.print($"Base   : p1={p1},    p2()={p2()}, p3()={p3()}");
            }
        }

        class Derived
                extends Base {
            Int p1() {
                return 11;
            }

            Int p2 = 22;

            Int p3() {
                return 33;
            }

            @Override
            Int p4 {
                @Override
                void report() {
                    super();
                    console.print($"Method report() was called {++count} times");
                }

                private Int count;
            }

            @Override
            void report() {
                super();

                console.print($"Derived: p1()={p1()}, p2={p2},  p3()={p3()}");
            }
        }
    }
}