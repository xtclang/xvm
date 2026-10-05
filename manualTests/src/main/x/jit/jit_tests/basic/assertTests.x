package assertTests {

    void run() {
        testPrimitiveValues(7, 8);
        testNullNullablePrimitive(Null, False);
        testSpecificNullJump(Null);
        testSpecificNullTest(Null);
        testXvmPrimitiveValues(1234567890, 9876543210);
        testNullNullableXvmPrimitive(Null, False);
        testStringValue("world", 7);
        testShortCircuitValues(8);
        testShortCircuitValues(7);
        testShortCircuitCall(False, 7);
        testShortCircuitCall(True, 7);
        testNullAssertion("value");
        testPrimitiveNullAssertion(7, 7, UInt64.MaxValue);
        testNullAssertionFallback(Null, "value");
        testNullAssertionFallback("value", Null);
        testTypeAssertion("value");
        testAssertionSnapshot(7);
        testAssertionChainSnapshot(7);
        testAssertionProperty();
        testMultiValueAssertion();
    }

    void testPrimitiveValues(Int value, Int? value2) {
        try {
            assert value == 0 || value2 == 0;
            assert as "assertion did not throw";
        } catch (IllegalState e) {
            assert e.text == \|"value == 0 || value2 == 0": value=7, value2=8
                              ;
        }
    }

    void testNullNullablePrimitive(Int? value, Boolean value2) {
        try {
            assert value != Null || value2;
            assert as "assertion did not throw";
        } catch (IllegalState e) {
            assert e.text == \|"value != Null || value2": value=Null, value2=False
                              ;
        }
    }

    <Value extends Nullable> void testSpecificNullJump(Value value) {
        if (value == Null) {
            return;
        }
        assert;
    }

    <Value extends Nullable> void testSpecificNullTest(Value value) {
        assert value == Null;
    }

    void testXvmPrimitiveValues(Int128 value, Int128? value2) {
        try {
            assert value == 0 || value2 == 0;
            assert as "assertion did not throw";
        } catch (IllegalState e) {
            assert e.text == \|"value == 0 || value2 == 0": value=1234567890, value2=9876543210
                              ;
        }
    }

    void testNullNullableXvmPrimitive(Int128? value, Boolean value2) {
        try {
            assert value != Null || value2;
            assert as "assertion did not throw";
        } catch (IllegalState e) {
            assert e.text == \|"value != Null || value2": value=Null, value2=False
                              ;
        }
    }

    void testStringValue(String value, Int value2) {
        try {
            assert value == "" || value2 == 0 && value2 == 1;
            assert as "assertion did not throw";
        } catch (IllegalState e) {
            assert e.text == \|"value == \"\" || value2 == 0 && value2 == 1": value=world, value2=7
                              ;
        }
    }

    void testShortCircuitValues(Int value) {
        try {
            // value=7 captures the right side before the next iteration skips it
            for (Int gate : 0..1) {
                assert gate < 0 || gate == 0 && value == 7;
            }
            assert as "assertion did not throw";
        } catch (IllegalState e) {
            // best-effort diagnostics omit a capture that can be skipped, even if it ran this time
            String values = value == 7 ? "gate=1, value=" : "gate=0, value=";
            assert e.text == $|"gate < 0 || gate == 0 && value == 7": {values}
                              ;
        }
    }

    void testShortCircuitCall(Boolean gate, Int value) {
        try {
            assert value < 0 || gate && isZero(value);
            assert as "assertion did not throw";
        } catch (IllegalState e) {
            String values = $"value={value}, gate={gate}, isZero(value)=";
            assert e.text == $|"value < 0 || gate && isZero(value)": {values}
                              ;
        }

        Boolean isZero(Int number) = number == 0;
    }

    void testNullAssertion(String? text) {
        // capture a diagnostic copy, but test the original variable so it is narrowed
        assert text != Null;
        assert text.size == 5;
    }

    void testPrimitiveNullAssertion(Int? small, Int128? large, UInt64? unsigned) {
        // the null tests must narrow the primitive payloads and null flags on these registers
        assert Null != small;
        assert large != Null;
        assert unsigned != Null;
        assert small + 1 == 8;
        assert large + 1 == 8;
        assert large + large == 14;
        assert large * 2 == 14;
        // a narrowed unsigned register must still select unsigned division, remainder, and shift
        assert unsigned / 2 == 9223372036854775807;
        assert unsigned % 3 == 0;
        assert unsigned >> 1 == 9223372036854775807;
    }

    void testNullAssertionFallback(String? text, String? fallback) {
        if (text == Null) {
            text = fallback;
            assert text != Null;
        }
        // both incoming paths prove that text is non-null
        assert text.size == 5;
    }

    void testTypeAssertion(Object value) {
        assert value.is(String);
        assert value.size == 5;
    }

    void testAssertionSnapshot(Int value) {
        try {
            // general comparisons must still use the captured left operand
            assert value == ++value;
            assert as "assertion did not throw";
        } catch (IllegalState e) {
            assert e.text == \|"value == ++value": value=7, ++value=8
                              ;
        }
        assert value == 8;
        assert (value) < ++value;
        assert value == 9;
    }

    void testAssertionChainSnapshot(Int value) {
        // chained comparisons use the list form of ensurePointInTime
        assert value < ++value < ++value;
        assert value == 9;
        assert (value) < ++value < ++value;
        assert value == 11;
    }

    void testAssertionProperty() {
        class Test {
            Int reads = 0;

            String? text.get() {
                return ++reads == 1 ? Null : "value";
            }

            void check() {
                Boolean caught = False;
                try {
                    // testing a property must use the capture, not call the getter a second time
                    assert text != Null;
                } catch (IllegalState e) {
                    caught = True;
                    assert e.text == \|"text != Null": text=Null
                                      ;
                }
                assert caught;
                assert reads == 1;
            }
        }

        new Test().check();
    }

    void testMultiValueAssertion() {
        try {
            // testing the first result must still capture both values for diagnostics
            assert pair().is(Int);
            assert as "assertion did not throw";
        } catch (IllegalState e) {
            assert e.text == \|"pair().is(Int)": pair()=(value, 7)
                              ;
        }

        (Object, Int) pair() = ("value", 7);
    }
}
