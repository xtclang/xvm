module TestSimple {
    @Inject Console console;

    class Test {
        String value {
            static String MSG = "static value";
            String msg = "instance value";
        } = "This is a value";

        Int x {
            static Int Bits = 1;
            @Override Int get() = super() + Bits;  // this used to fail to compile
        }

        Int y {
            static Int Bits = Test.x.Bits + 1;
            @Override Int get() = super() + Bits;
        }

        Int z {
            static Int Bits = x.Bits + 2;  // this used to fail to compile
            @Override Int get() = super() + Bits;
        }

        static String test() {
            return value.MSG;
        }
    }

    void run() {
        String MSG = Test.value.MSG; // this used to fail to compile
        console.print($"{MSG=}");

        Test t = new Test();

        Property<Test, String, Ref<String>> prop = t.value;
        console.print($"{prop=} {&prop.actualType=}");

        prop.Implementation ref = prop.of(t);
        console.print($"{&ref.actualType=} {ref.get()=}");

        console.print($"{t.x=} {t.y=}");

        console.print(Test.test());
    }
}