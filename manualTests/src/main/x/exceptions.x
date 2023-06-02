module TestTry {
    @Inject ecstasy.io.Console console;

    void run() {
        console.print("Exception tests:");

        try {
            testBasic();
        } catch (Exception e) {
            console.print("UNEXPECTED THROW in testBasic(): " + e);
        }

        try {
            testUsing();
        } catch (Exception e) {
            console.print("UNEXPECTED THROW in testUsing(): " + e);
        }

        try {
            testFinally();
            console.print("TEST ERROR!!!");
        } catch (Exception e) {
            console.print("expected throw in testFinally(): " + e);
        }

        try {
            testAssert(-1);
            console.print("TEST ERROR!!!");
        } catch (Exception e) {
            console.print("expected throw in testAssert(): " + e);
        }

        try {
            testAssert(17);
            console.print("TEST ERROR!!!");
        } catch (Exception e) {
            console.print("expected throw in testAssert(): " + e);
        }

        try {
            testAssert(3);
            console.print("(expected non-throw in testAssert())");
        } catch (Exception e) {
            console.print("TEST ERROR!!!  Unexpected throw in testAssert(): " + e);
        }

        try {
            testAssert2();
            console.print("TEST ERROR!!!");
        } catch (Exception e) {
            console.print("expected throw in testAssert2(): " + e);
        }

        testAssertOnce(True);
        testAssertOnce(False);

        testAssertSample();

        testSwitch(0);
        testSwitch(1);

        console.print("\nException tests: finished!");
    }

    void testThrow() {
        console.print("in testThrow()");
        throw new IllegalState("test");
    }

    void testBasic() {
        console.print("\n** testBasic()");

        try {
            testThrow();
            console.print("DIDN'T THROW!");
        } catch (Exception e) {
            console.print("caught: " + e);
        }

        console.print("done testBasic()");
    }

    void testUsing() {
        console.print("\n** testUsing()");

        try {
            using (ByeBye bye = new ByeBye()) {
                testThrow();
                console.print("DIDN'T THROW!");
            }
        } catch (Exception e) {
            console.print("ok");
        }

         try (ByeBye bye = new ByeBye()) {
            console.print(bye);
        } finally {
            console.print("in finally: " + bye);
        }

        console.print("done");
    }

    class ByeBye
            implements ecstasy.Closeable {
        construct() {
            console.print("hello!");
        }

        @Override
        void close(Exception? cause = Null) {
            console.print("bye-bye!");
        }
    }

    void testFinally() {
        console.print("\n** testFinally()");

        FOR: for (Int i : 1..2) {
            console.print("iteration " + i);
            TRY: try {
                if (FOR.last) {
                    console.print("throwing exception inside try");
                    testThrow();
                } else {
                    console.print("not throwing exception inside try");
                    continue;
                }
            } finally {
                console.print("exception in finally: " + TRY.exception);
            }
        }

        console.print("done testFinally() - which shouldn't happen!");
    }

    /**
     * ** testAssert(-1)
     * OutOfBounds: i >= 0 && i < size(), i=-1
     *
     * ** testAssert(17)
     * OutOfBounds: i >= 0 && i < size(), i=17, size()=6
     *
     * ** testAssert(3)
     * (no assertion)
     */
    void testAssert(Int i) {
        console.print($"\n** testAssert({i})");

        assert:bounds i >= 0 && i < size();
    }

    Int size() {
        return 6;
    }

    void testAssert2() {
        console.print("\n** testAssert2()");

        Int x = 4;
        assert ++x <= 4;
    }

    void testAssertOnce(Boolean firstTime) {
        console.print($"\n** testAssertOnce({firstTime})");

        Int x = 42;
        try {
            assert:once x < size();

            // the assertion passed, which it shouldn't do, unless this is the second time through
            console.print(firstTime ? "[1st] ERR: should have asserted" : "[2nd] OK: skipped");
        } catch (Exception e) {
            console.print(firstTime ? "[1st] OK: assert" : "[2nd] ERR: should have skipped");
            console.print(e.text);
        }
    }

    void testAssertSample() {
        console.print("\n** testAssertSample()");

        Int x   = 99;
        Int ok  = 0;
        Int err = 0;
        for (Int i : 1..1000) {
            try {
                assert:rnd(100) x < size();
                ++ok;
            } catch (OutOfBounds e) {
                assert;
            } catch (Exception e) {
                ++err;
            } finally {
                ++x;
            }
        }

        console.print($"results: ok={ok}, errs={err} (should be ~10)");
    }

    void testSwitch(Int n) {
        console.print($"\n** testSwitch({n})");

        try {
            switch (n) {
            case -1:
                return;

            default: {
                try {
                    n = 10/n;
                    break;
                } catch (Exception e) {
                     console.print($"exception {e}");
                     throw e;
                } finally {
                    console.print("inner finally");
                }
            }
            }
        } finally {
            console.print("outer finally");
            if (n == 0) {
                // eat the exception
                return;
            }
        }
    }
}