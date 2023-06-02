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

        Int i = 0;
        while (i < 5) {
            function Int() f = () -> ++i;
            console.print("result=" + f());
            // console.print("i=" + i + ", result=" + f() + ", i=" + i);
        }

        // test for the capture of an unassigned variable
        Int j;
        function void() f2 = () -> {j = ++i;};
        f2();
        console.print("j=" + &j.get());
    }

    void testComplexCapture() {
        console.print("\n** testComplexCapture()");

        Int i = 0;
        while (i < 5) {
            function Int() f1 = () -> i;
            console.print("result=" + f1());    // initially would appear to be "Int", but must be "Ref<Int>"
            function Int() f2 = () -> ++i;
            console.print("result=" + f2());
        }
    }
}