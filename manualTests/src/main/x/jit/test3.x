module test3.examples.org {

    import ecstasy.io.IOException;

    @Inject Console console;

    void run( ) {
        testTry1();
        console.print(testTry2());
        testAssert1(False);
        testAssert2(False);
    }

    void testTry1() {
        TRY:
        try {
            testThrow(0);
        } catch (IOException e) {
            console.print("1) IOException caught");
        } catch (Unsupported e) {
            console.print("1) Unsupported caught");
            throw e;
        } finally {
            console.print("1) Finally: ", True);
            console.print(TRY.exception?.text : "no exception");
        }
    }

    Int testTry2() {
        try {
            for (Int i : 0..2) {
                try {
                    testThrow(i);
                } catch (IOException e) {
                    console.print("2) IOException caught");
                    continue;
                } catch (Unsupported e) {
                    console.print("2) Unsupported caught");
                    return i + 10;
                } finally {
                    console.print("2) Finally: ", True);
                    console.print(i);
                    if (i == 2) {
                        return i + 40;
                    }
                }
            }
            return -1;
        } finally {
            console.print("2) Done");
        }
    }

    void testThrow(Int i) {
        if (i < 0) {
            return;
        }
        if (i == 0) {
            throw new IOException("Test IO");
        } else if (i == 1) {
            throw new Unsupported("Test Unsupported");
        } else {
            throw new IllegalState("Test IllegalState");
        }
    }

    void testAssert1(Boolean flag) {
        try {
            assert flag as "Flag is not set";
        } catch (IllegalState e) {
            console.print("Assert 1) ", True);
            console.print(e.text);
        }
    }

    void testAssert2(Boolean flag) {
        try {
            assert Int i := next(flag), Int j := next(flag);
        } catch (IllegalState e) {
            console.print("Assert 2) ", True);
            console.print(e.text);
        }
    }

    conditional Int next(Boolean flag) = False;
}