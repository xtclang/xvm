package condMixinTests {
    TestConsole console = new TestConsole();

    void run() {

        test1();
        test2();
    }

    void test1() {
        import t1.*;

        console.reset();
        Test<String> ts = new Test("hello");
        assert ts.size() == 5;
        assert ts.Element.is(Type<String>);
        assert console.output() == \|MixS
                                    |
                                    ;

        Test<Int> ti = new Test(42);
        assert ti.Element.is(Type<Int>);
        assert console.output() == \|MixS
                                    |MixN
                                    |MixS
                                    |
                                    ;
        assert ti.value() == 42;
    }

    void test2() {
        import t2.*;

        console.reset();
        Test<String> ts = new Test("hello");
        assert ts.Element.is(Type<String>);
        assert console.output() == \|MixS
                                    |
                                    ;
        assert ts.size() == 5;

        Test<Int> ti = new Test(42);
        assert ti.Element.is(Type<Int>);
        assert console.output() == \|MixS
                                    |MixN
                                    |
                                    ;
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
                console.print("MixS");
            }

            Int size() = el.estimateStringLength();
        }

        static mixin MixN<Element extends Number>
                into Test<Element> {
            construct() {
                console.print("MixN");
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
                console.print("MixS");
            }

            Int size() = el.size;
        }

        static mixin MixN<Element extends Int>
                into Test<Element> {
            construct() {
                console.print("MixN");
            }

            Int value() = el;
        }
   }
}
