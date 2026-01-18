package CondMixinTests {
    @Inject Console console;

    void run() {
        console.print(">>>> Running CondMixinTests >>>>");

        test1();
        test2();
    }

    void test1() {
        import t1.*;

        Test<String> ts = new Test("hello");
        console.print("1a) Element=", True);
        console.print(ts.Element);
        assert ts.size() < 6; // TODO: the result is incorrect (zero) until we compile String.x

        Test<Int> ti = new Test(42);
        console.print("1b) Element=", True);
        console.print(ti.Element);
        assert ti.value() == 42;
    }

    void test2() {
        import t2.*;

        Test<String> ts = new Test("hello");
        console.print("2a) Element=", True);
        console.print(ts.Element);
        assert ts.size() == 5;

        Test<Int> ti = new Test(42);
        console.print("2b) Element=", True);
        console.print(ti.Element);
        assert ti.value() == 42;
    }

    package t1 {
        class Test<Element>(Element el)
            incorporates conditional MixN<Element extends Number>
            incorporates conditional MixS<Element extends Stringable> {
        }

        static mixin MixS<Element extends Stringable>
                into Test<Element> {
            construct() {
                console.print("In t1.MixS");
            }

            Int size() = el.estimateStringLength();
        }

        static mixin MixN<Element extends Number>
                into Test<Element> {
            construct() {
                console.print("In t1.MixN");
            }

            Int value() {
                if (Int n := el.is(Int)) {
                    return n;
                }
                return -1;
            }
        }
    }

    package t2 {
        class Test<Element>(Element el)
            incorporates conditional MixN<Element extends Int>
            incorporates conditional MixS<Element extends String> {
        }

        static mixin MixS<Element extends String>
                into Test<Element> {
            construct() {
                console.print("In t2.MixS");
            }

            Int size() = el.size;
        }

        static mixin MixN<Element extends Int>
                into Test<Element> {
            construct() {
                console.print("In t2.MixN");
            }

            Int value() = el;
        }
   }
}