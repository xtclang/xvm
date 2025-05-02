module TestProps {
    @Inject Console console;

    void run() {
        testStandardProperty();
        testMethodProperty();
        testPropertyProperty();
        testLazyProperty();
        testModuleProperty();
        testDelegation();
        testAccess();
        testImmutableVar();
    }

    class Standard(Int x);

    void testStandardProperty() {
        Standard s = new Standard(1);
        Int iterations = 100_000;

        @Inject Timer timer;

        timer.start();
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

    void testPropertyProperty() {
        TestProperty test = new TestProperty();
        assert test.&value.get() == 42;

        test.&value.setValue(1);
        assert test.value == 1;

        test.&value.assignValue(2);
        assert test.value == 2;
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

            console.print($" - in showMethodProperty(), {++x=}, {y=}");
        }

        Int value {
            void setValue(Int newValue) {
                set(newValue);
                assert get() == newValue;
            }

            void assignValue(Int newValue) {
                value = newValue;
                assert value == newValue;
            }
        } = 42;
    }

    static Int calcStaticProperty() {
        @Inject ecstasy.io.Console console;

        console.print(" - in calcStaticProperty()");
        return 3;
    }

    void testLazyProperty() {
        assert lazy == 42;
    }

    @Lazy Int lazy.calc() = 42;

    static void testModuleProperty() {
        this:module.console.print("\n** testModuleProperty()");
    }

    void testDelegation() {
        console.print("\n** testDelegation()");

        class NamedNumber(String name, Int number)
                delegates Stringable(name) {}

        class NamedNumber2(String name, Int number)
                delegates Stringable-Object(name) {}

        NamedNumber nn = new NamedNumber("answer", 42);
        console.print($"{nn.estimateStringLength()=}");
        console.print($"{nn.toString()=}");

        NamedNumber2 nn2 = new NamedNumber2("answer", 42);
        console.print($"{nn2.estimateStringLength()=}");
        console.print($"{nn2.toString()=}");
    }

    void testAccess() {
        console.print("\n** testAccess()");

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
                console.print($"Base   : {p1=},    {p2()=}, {p3()=}");
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

                console.print($"Derived: {p1()=}, {p2=},  {p3()=}");
            }
        }
    }

    void testImmutableVar() {
        console.print("\n** testImmutableVar()");

        Test t = new Test();
        t.testProps();
        t.testVars();

        class Test {
            String[] assignedProp = new String[];

            @Unassigned
            String[] unassignedProp;

            @Lazy String[] calculatedProp.calc() {
                return [""];
            }

            @Lazy String[] uncalculatedProp.calc() {
                return [""];
            }

            void testProps() {
                Var<String[]> varA = &assignedProp;

                varA.makeImmutable();
                validateImmutable(varA);
                validateImmutable(assignedProp);

                Var<String[]> varU = &unassignedProp;
                varU.makeImmutable();
                validateImmutable(varU);

                String[] strings = calculatedProp;
                Var<String[]> varC = &calculatedProp;
                varC.makeImmutable();
                validateImmutable(varC);
                validateImmutable(strings);

                Var<String[]> varUC = &uncalculatedProp;
                varUC.makeImmutable();
                validateImmutable(varUC);
                try {
                    strings = uncalculatedProp; // too late; cannot be calculated anymore
                    assert;
                } catch (ReadOnly ignore) {}

            }

            void testVars() {
                String[] assignedVar = new String[];
                Var<String[]> varA = &assignedVar;

                varA.makeImmutable();
                validateImmutable(varA);
                validateImmutable(assignedVar);

                String[] unassignedVar;
                Var<String[]> varU = &unassignedVar;
                varU.makeImmutable();
                validateImmutable(varU);
            }

            void validateImmutable(Var<String[]> var) {
                assert !var.assigned || var.is(immutable);
                try {
                    var.set([""]);
                    assert;
                } catch (ReadOnly ignore) {}
            }

            void validateImmutable(String[] referent) {
                assert referent.is(immutable);
                assert referent.mutability == Constant;
            }
        }
    }
}