package MixinTests {
    @Inject Console console;

    void run() {
        console.print(">>>> Running MixinTests >>>>");

        test1();
        test2();
        test3();
        test4();
    }

    void test1() {
        import t1.*;

        Base b = new Base();
        assert b.f1() == 42;
    }

    void test2() {
        import t2.*;

        Base b = new Base();
        assert b.f() == 42;
    }

    void test3() {
        import t3.*;

        Derived d = new Derived();
        assert d.f() == 42;
    }

    void test4() {
        import t4.*;

        Derived d = new Derived();
        assert d.f() == 42;
    }

    package t1 {
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

    package t2 {
        class Base
                incorporates Mix {
            @Override Int f() = super() + 1;
        }

        mixin Mix into Base {
            Int f() = 41;
        }
    }

    package t3 {
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

    package t4 {
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
}