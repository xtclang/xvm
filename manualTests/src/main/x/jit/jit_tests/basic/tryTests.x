package tryTests {

    import ecstasy.io.IOException;

    static TestConsole console = new TestConsole();

    void run() {
        testTry1();
        assert console.output() == \|IOException caught
                                    |no exception
                                    |
                                    ;
        console.reset();
        assert testTry2() == 11;
        assert console.output() == \|IOException caught
                                    |Finally: 0
                                    |Unsupported caught
                                    |Finally: 1
                                    |Done
                                    |
                                    ;
        testAssert1(False);
        testAssert2(False);
        testUsing();
        testTryWithIntResource();
        testBreakScope();
    }

    void testTry1() {
        TRY:
        try {
            testThrow(0);
        } catch (IOException e) {
            assert e.text == "Test IO";
            console.print("IOException caught");
        } catch (Unsupported e) {
            throw e;
        } finally {
            if (TRY.exception == Null) {
                console.print("no exception");
            } else {
                console.print("exception");
            }
        }
    }

    Int testTry2() {
        try {
            for (Int i : 0..2) {
                try {
                    testThrow(i);
                } catch (IOException e) {
                    assert e.text == "Test IO";
                    console.print("IOException caught");
                    continue;
                } catch (Unsupported e) {
                    assert e.text == "Test Unsupported";
                    console.print("Unsupported caught");
                    return i + 10;
                } finally {
                    console.print($"Finally: {i}");
                    if (i == 2) {
                        return i + 40;
                    }
                }
            }
            return -1;
        } finally {
            console.print("Done");
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
            assert e.text == "Flag is not set";
            return;
        }
        assert as "Assertion did not fail";
    }

    void testAssert2(Boolean flag) {
        try {
            assert Int i := next(flag), Int j := next(flag);
        } catch (Exception e) {
            assert e.text == "\"Int i := next(flag)\": flag=False";
            return;
        }
        assert as "Assertion did not fail";
    }

    conditional Int next(Boolean flag) = False;

    void testUsing() {
        val closeable = new TestClose();
        try {
            using (closeable) {
                assert Int i := next(False);
            }
        } catch (Exception expected) {
            assert closeable.value == -1;
            return;
        }
        assert as "Failed to throw";
    }

    class TestClose(Int value = 0)
            implements Closeable {
        @Override
        void close(Exception? cause = Null) {
            value = -1;
        }
    }

    void testTryWithIntResource() {
        Int n1 = 100;
        try (Int n2 = n1) {
            n1++;
        } finally {
            n1 = n2;
        }
        assert n1 == 100;
    }

    void testBreakScope() {
        Int loopCount = 0;

        while (loopCount < 1) {
            Byte   scoped1 = 1;
            String scoped2 = "";
            if (scoped1 > 0) {
                break;
            }
            ++loopCount;
        }

        assert loopCount == 0 as $"loopCount={loopCount}";
    }
}
