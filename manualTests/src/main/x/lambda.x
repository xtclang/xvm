module TestLambda {
    @Inject ecstasy.io.Console console;

    void run() {
        console.print("Lambda tests:");

        testVoid();
        testBasic();
        testEffectivelyFinalCapture();
        testThisCapture();
        testRefCapture();
        testVarCapture();
        testComplexCapture();
        testGenericMethodReference();
    }

    void testVoid() {
        console.print("\n** testVoid()");

        function void() f = () -> { console.print("in the lambda!"); };

        f();
    }

    void testBasic() {
        console.print("\n** testBasic()");

        function Int() f = () -> 4;
        console.print($"f={f};\nf()={f()}");
    }

    void testEffectivelyFinalCapture() {
        console.print("\n** testEffectivelyFinalCapture()");

        Int i = 4;
        function Int() f = () -> i;
        console.print("result=" + f());
    }

    void testThisCapture() {
        console.print("\n** testThisCapture()");

        function String() f = () -> foo();
        console.print("result=" + f());
    }
    String foo() {
        return "hello";
    }

    void testRefCapture() {
        console.print("\n** testRefCapture()");

        Int i = 0;
        do {
            function Int() f = () -> i;
            console.print("result=" + f());
        } while (i++ < 5);
    }

    void testVarCapture() {
        console.print("\n** testVarCapture()");

        @Volatile Int i = 0;
        while (i < 5) {
            function Int() f = () -> ++i;
            console.print("result=" + f());
        }

        // test for the capture of an unassigned variable
        @Volatile Int j;
        function void() f2 = () -> {j = ++i;};
        f2();
        console.print("j=" + &j.get());
    }

    void testComplexCapture() {
        console.print("\n** testComplexCapture()");

        @Volatile Int i = 0;
        while (i < 5) {
            function Int() f1 = () -> i;
            console.print("result=" + f1());    // initially would appear to be "Int", but must be "Ref<Int>"
            function Int() f2 = () -> ++i;
            console.print("result=" + f2());
        }
    }

    void testGenericMethodReference() {
        console.print("\n** testGenericMethodReference()");

        // the reference binds describe's type parameter to Int
        function String(Int) f = describe;
        String result = f(42);
        console.print("result=" + result);
        assert result == "Int 42";
    }
    <T> String describe(T value) = $"{T} {value}";
}
