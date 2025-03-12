module TestCompilerErrors {
    // arrays
    void testAOOB1() {
        Object test = ["hello", "cruel", "world", "!"] [-1];
    }
    void testAOOB2() {
        Object test = ["hello", "cruel", "world", "!"] [4];
    }
    void testAOOB3() {
        Object test = ["hello", "cruel", "world", "!"] [1..4];
    }
    void testAOOB4() {
        Object test = ["hello", "cruel", "world", "!"] [4..1];
    }
    void testAOOB5() {
        Object test = ["hello", "cruel", "world", "!"] [-1..1];
    }
    void testAOOB6() {
        Object test = ["hello", "cruel", "world", "!"] [1..-1];
    }
    void testConstruct() {
        Int[] array = new Int[7, (i) -> -1];
    }
    // tuples
    void testTOOB1() {
        Tuple test = (3, "blind", "mice", "!") [-1..1];
    }
    void testTOOB2() {
        Tuple test = (3, "blind", "mice", "!") [1..4];
    }
    void testTOOB3() {
        Tuple test = (3, "blind", "mice", "!") [4..1];
    }
    void testTOOB4() {
        Tuple test = (3, "blind", "mice", "!") [1..-1];
    }
    void testTOOB5() {
        Object test = (3, "blind", "mice", "!") [-1];
    }
    void testTOOB6() {
        Object test = (3, "blind", "mice", "!") [4];
    }

    // methods
    class TestMethods {
        static void testMethod1() {
            function void () m1 = testMethod2;      // no "this"
            function void () m2 = Test.testMethod2; // no target
        }

        void testMethod2() {}
    }

    // def assignment
    void defAssign1(String? s = Null, Int i = 0) {
        if ((s != Null) || (i == 0)) {
            i = s.size;  // should not compile
        }
    }

    void defAssign2(String? s = Null, Int i = 0) {
        if ((s == Null) && (i == 1)) {} else {
            i = s.size; // should not compile
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
                super(); // should not compile
            }
        }

        class Derived
                extends Base {
            @Override
            Int f(Boolean flag=False) {
                return super(flag); // should not compile
            }
        }

        mixin Mix
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
            case Int: "Int";  // should not compile: unreachable
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
                Child ci = bi.new Child(); // should not compile: type is B<Int>.C; not assignable to B<BT>.C
            }
        }
    }

    void testUnassigned() {
        @Custom Int i;

        Int j = i; // should not compile - unassigned
    }

    annotation Custom<Referent>
            into Var<Referent> {}

    void testFinal(Boolean f) {
        @Final Int i;

        if (f) {
            i = 1;
        }

        i = 2;  // should not compile - cannot be assigned to
    }

    enum Group {A, B, C, D, E, F}
    Int testDuplicateCase(Group g) {
        return switch (g) // should not compile; default is missing
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
        Parent.Child child = new Parent.Child(); // should not compile
    }
}