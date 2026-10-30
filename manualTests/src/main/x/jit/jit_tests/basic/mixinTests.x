package mixinTests {
    void run() {

        testInit();
        testSuperCall();
        testClassChain();
        testMixinChain();
        testCovariant();
        testAutoNarrow();
        testPrivateCalls();
        testValidators();
        testInheritance();
    }

    void testInit() {
        import init.*;

        Base b = new Base();
        assert b.f1() == 42;
    }

    void testSuperCall() {
        import superCall.*;

        Base b = new Base();
        assert b.f() == 42;
    }

    void testClassChain() {
        import classChain.*;

        Derived d = new Derived();
        assert d.f() == 42;
    }

    void testMixinChain() {
        import mixinChain.*;

        Derived d = new Derived();
        assert d.f() == 42;
    }

    void testCovariant() {
        import covariant.*;

        Base base = new Base();
        assert base.self().value() == 42;
    }

    void testAutoNarrow() {
        import autoNarrow.*;

        Base base = new Base();
        assert base.self().value() == 42;
    }

    void testPrivateCalls() {
        import privateCalls.*;

        Base base = new Base();
        assert base.nestedValue() == 41;
        assert base.privateValue() == 1;
    }

    void testValidators() {
        import validators.*;

        new Derived(1, 1);

        Boolean baseRejected = False;
        try {
            new Derived(-1, 1);
        } catch (IllegalState e) {
            baseRejected = e.text == "base validator";
        }
        assert baseRejected;

        Boolean mixinRejected = False;
        try {
            new Derived(11, 1);
        } catch (IllegalState e) {
            mixinRejected = e.text == "mixin validator";
        }
        assert mixinRejected;

        Boolean derivedRejected = False;
        try {
            new Derived(1, -1);
        } catch (IllegalState e) {
            derivedRejected = e.text == "derived validator";
        }
        assert derivedRejected;
    }

    void testInheritance() {
        import inheritance.*;

        Mix1 child = new C();
        Mix0 parent = asParent(child);
        assert parent.value() == "parent";

        Mix0 asParent(Mix1 child) = child;
    }

    package init {
        class Base incorporates Mix {
            Int f1() = f0() + 1;

            // JIT compiler produced Java code for Base contains the following synthetic code:
            // construct() {
            //     Mix$construct();
            // }
            // Int value;
            //
            // Mix$construct() {
            //     value = 41;
            // }
            // Int f0() = value;
        }

        mixin Mix into Base {
            Int value;
            construct() {
                value = 41;
            }
            Int f0() = value;
        }
    }

    package superCall {
        class Base
                incorporates Mix {
            @Override Int f() = super() + 1;
        }

        mixin Mix into Base {
            Int f() = 41;
        }
    }

    package classChain {
        class Base
                incorporates Mix {
            @Override Int f() = super() + 1;
        }

        mixin Mix into Base {
            Int f() = 40;
        }

        class Derived extends Base {
            @Override Int f() = super() + 1;
        }
    }

    package mixinChain {
        class Base {}

        mixin Mix0 into Base {
            Int f() = 40;
        }

        mixin Mix1 extends Mix0 {
            @Override Int f() = super() + 1;
        }

        class Derived extends Base incorporates Mix1 {
            @Override Int f() = super() + 1;
        }
    }

    package covariant {
        interface Root {
            Root self();
        }

        class Base implements Root incorporates Mix {
            Int value() = 42;

            @Override
            Base self() = super().as(Base);
        }

        mixin Mix into Base {
            @Override
            Mix self() = this;
        }
    }

    package autoNarrow {
        class Base incorporates Mix {
            Int value() = 42;
        }

        mixin Mix into Base {
            Mix! self() = this;
        }
    }

    package privateCalls {
        class Base incorporates Mix1 {}

        mixin Mix0 into Base {
            Int nestedValue() {
                private Int nested() = 41;
                return nested();
            }

            Int privateValue() = privateHelper();
            private Int privateHelper() = 1;
        }

        mixin Mix1 extends Mix0 {}
    }

    package validators {
        class Base(Int value) {
            assert() {
                assert value >= 0 as "base validator";
            }
        }

        mixin Mix into Base {
            assert() {
                assert value <= 10 as "mixin validator";
            }
        }

        class Derived(Int value, Int extra)
                extends Base(value)
                incorporates Mix {
            assert() {
                assert extra >= 0 as "derived validator";
            }
        }
    }

    package inheritance {
        class C incorporates Mix1 {}

        mixin Mix0 into C {
            String value() = "parent";
        }

        mixin Mix1 extends Mix0 {}
    }
}
