module TestCompilerErrors {
    // arrays
    void testAOOB1() {
        Object test = ["hello", "cruel", "world", "!"] [-1];  // expect-error: COMPILER-95
    }
    void testAOOB2() {
        Object test = ["hello", "cruel", "world", "!"] [4];  // expect-error: COMPILER-95
    }
    void testAOOB3() {
        Object test = ["hello", "cruel", "world", "!"] [1..4];  // expect-error: COMPILER-95
    }
    void testAOOB4() {
        Object test = ["hello", "cruel", "world", "!"] [4..1];  // expect-error: COMPILER-95
    }
    void testAOOB5() {
        Object test = ["hello", "cruel", "world", "!"] [-1..1];  // expect-error: COMPILER-95
    }
    void testAOOB6() {
        Object test = ["hello", "cruel", "world", "!"] [1..-1];  // expect-error: COMPILER-95
    }
    void testConstruct() {
        Int[] array = new Int[7, (i) -> -1];  // expect-error: COMPILER-NI (multi-dim arrays)
    }
    // tuples
    void testTOOB1() {
        Tuple test = (3, "blind", "mice", "!") [-1..1];  // expect-error: COMPILER-95
    }
    void testTOOB2() {
        Tuple test = (3, "blind", "mice", "!") [1..4];  // expect-error: COMPILER-95
    }
    void testTOOB3() {
        Tuple test = (3, "blind", "mice", "!") [4..1];  // expect-error: COMPILER-95
    }
    void testTOOB4() {
        Tuple test = (3, "blind", "mice", "!") [1..-1];  // expect-error: COMPILER-95
    }
    void testTOOB5() {
        Object test = (3, "blind", "mice", "!") [-1];  // expect-error: COMPILER-95
    }
    void testTOOB6() {
        Object test = (3, "blind", "mice", "!") [4];  // expect-error: COMPILER-95
    }

    // methods
    class TestMethods {
        static void testMethod1() {
            function void () m1 = testMethod2;      // expect-error: COMPILER-52 (no "this")
            function void () m2 = Test.testMethod2; // expect-error: COMPILER-36 (no target)
        }

        void testMethod2() {}
    }

    // def assignment
    void defAssign1(String? s = Null, Int i = 0) {
        if ((s != Null) || (i == 0)) {
            i = s.size;  // expect-error: COMPILER-36 (s may be Null)
        }
    }

    void defAssign2(String? s = Null, Int i = 0) {
        if ((s == Null) && (i == 1)) {} else {
            i = s.size; // expect-error: COMPILER-36 (s may be Null)
        }
    }

    package TestVirtualSuper {
        interface Iface {
            void f();
        }

        @Mix
        class Base
                implements Iface {
            @Override
            void f(Boolean flag = False) {
                super(); // expect-error: COMPILER-53 (no super method)
            }
        }

        class Derived
                extends Base {
            @Override
            Int f(Boolean flag=False) {
                return super(flag); // expect-error: COMPILER-152 (return count mismatch)
            }
        }

        annotation Mix
            into Base {
            @Override
            void f(Boolean flag=False) {
                super(flag);
            }
        }
    }

    void testUnreachable(Object o) {
        String s = switch (o.is(_)) {
            case IntNumber, FPNumber: "Number";
            case Int: "Int";  // expect-error: COMPILER-46 (unreachable)
            default:  "other";
        };
    }

    package TestGenerics {
        class Base<BaseType> {
            Base<BaseType>? nextBase;
            Child? nextChild;

            class Child {}

            Base!<> createBase() {
                return new Base<String>();
            }

            void createChildTest1() {
                Base<Int> bi = new Base<Int>();
                Child ci = bi.new Child(); // expect-error: COMPILER-43 (B<Int>.C is not a B<BT>.C)
            }
        }
    }

    void testUnassigned() {
        @Custom Int i;

        Int j = i; // expect-error: COMPILER-81 (not definitely assigned)
    }

    annotation Custom<Referent>
            into Var<Referent> {}

    void testFinal(Boolean f) {
        @Final Int i;

        if (f) {
            i = 1;
        }

        i = 2;  // expect-error: COMPILER-82 (cannot be assigned to)
    }

    enum Group {A, B, C, D, E, F}
    Int testDuplicateCase(Group g) {
        return switch (g) // expect-error: COMPILER-76 (default is missing)
            {
            case C:    1;
            case B..D: 2;
            case A..E: 3;
            };
    }

    void testVirtualChild() {
        class Parent {
            class Child {}
        }
        Parent.Child child = new Parent.Child(); // expect-error: COMPILER-203 (no parent instance)
    }

    package testAccess {
        class Base(Int valBasePro, Int valBasePri) {
            protected Int valBasePro;
            protected Int fBasePro() = valBasePro;
            private   Int valBasePri;
            private   Int fBasePri() = valBasePri;

            void testAccess(Derived node) {
                // not accessible
                Int _ = node.valDerivedPro;  // expect-error: COMPILER-162
                Int _ = node.fDerivedPro();  // expect-error: COMPILER-177
                Method m = node.fDerivedPro;  // expect-error: COMPILER-177

                // not found
                assert node.valBasePri > 0;  // expect-error: COMPILER-36
                assert node.fBasePri() > 0;  // expect-error: COMPILER-56
            }
        }

        class Derived(Int valBasePro, Int valDerivedPri, Int valDerivedPro)
                extends Base(valBasePro, valDerivedPri) {
            private   Int valDerivedPri;
            protected Int valDerivedPro;
            protected Int fDerivedPro() = valDerivedPro;
        }
    }

    package testAnnos {
        // this should generate an "unreachable method" warning
        annotation WebService(String path) into service {
            void route() = TODO
        }

        @WebService("/")
        service Main extends Base {
            @Override
            void route() = TODO
        }

        service Base {
            void route() = TODO
        }
    }

    package testUnreachableCondition {
        class Test {
            Boolean test(Int[] vals) {
                if (True || vals.all(v -> v > 0)) {  // expect-error: COMPILER-208 (after True ||)
                    return True;
                }
                return False;
            }
        }
    }

    package testInvalidAnnotation {
        void test() {
            @AutoFreezable  // expect-error: VERIFY-32 (not compatible with the annotation's into)
            immutable Test t = new Test();

            String s = t.s;  // expect-error: COMPILER-38 (unresolvable)

            class Test {
                String s = "Hi there";
            }
        }
    }
}
