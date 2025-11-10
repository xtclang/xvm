module test6.examples.org {
    @Inject Console console;

    void run() {
        test1();
    }

    void test1() {
        TestA<String> t = new TestA("hello");
        console.print("Element: ", True);
        console.print(t.Element);
        // console.print(t.size());
    }

    class TestA<Element>(Element el)
        incorporates conditional MixN<Element extends Number>
        incorporates conditional MixS<Element extends Stringable> {
    }

    static mixin MixS<Element extends Stringable>
            into TestA<Element> {
        Int size() {
            return el.estimateStringLength();
        }
    }

    static mixin MixN<Element extends Number>
            into TestA<Element> {
        Int value() {
            if (Int n := el.is(Int)) {
                return n;
            }
            return -1;
        }
    }
}