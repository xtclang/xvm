module test6.examples.org {
    @Inject Console console;

    void run() {
        test1();
    }

    void test1() {
        TestA<String> ts = new TestA("hello");
        console.print("Element: ", True);
        console.print(ts.Element);
        // console.print(ts.size());
    }

    class TestA<Element>(Element el)
        incorporates conditional MixN<Element extends Number>
        incorporates conditional MixS<Element extends Stringable> {
    }

    static mixin MixS<Element extends Stringable>
            into TestA<Element> {
        construct() {
            console.print("In MixS");
        }

        Int size() {
            return el.estimateStringLength();
        }
    }

    static mixin MixN<Element extends Number>
            into TestA<Element> {
        construct() {
            console.print("In MixN");
        }

        Int value() {
            if (Int n := el.is(Int)) {
                return n;
            }
            return -1;
        }
    }
}