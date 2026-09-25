package assertTests {

    @Inject Console console;

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
    }

    void testPrimitiveValues(Int value, Int? value2) {
        try {
            assert value == 0 || value2 == 0;
            assert as "expected IllegalState";
        } catch (IllegalState e) {
            assertText(e, "\"value == 0 || value2 == 0\": value=7, value2=8");
        }
    }

    void testNullNullablePrimitive(Int? value, Boolean value2) {
        try {
            assert value != Null || value2;
            assert as "expected IllegalState";
        } catch (IllegalState e) {
            assertText(e, "\"value != Null || value2\": value=Null, value2=False");
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
            assert as "expected IllegalState";
        } catch (IllegalState e) {
            assertText(e, "\"value == 0 || value2 == 0\": value=1234567890, value2=9876543210");
        }
    }

    void testNullNullableXvmPrimitive(Int128? value, Boolean value2) {
        try {
            assert value != Null || value2;
            assert as "expected IllegalState";
        } catch (IllegalState e) {
            assertText(e, "\"value != Null || value2\": value=Null, value2=False");
        }
    }

    void testStringValue(String value, Int value2) {
        try {
            assert value == "" || value2 == 0 && value2 == 1;
            assert as "expected IllegalState";
        } catch (IllegalState e) {
            assertText(e, "\"value == \\\"\\\" || value2 == 0 && value2 == 1\": value=world, value2=7");
        }
    }

    void testShortCircuitValues(Int value) {
        try {
            // value=7 captures the right side before the next iteration skips it
            for (Int gate : 0..1) {
                assert gate < 0 || gate == 0 && value == 7;
            }
            assert as "expected IllegalState";
        } catch (IllegalState e) {
            // best-effort diagnostics omit a capture that can be skipped, even if it ran this time
            String values = value == 7 ? "gate=1, value=" : "gate=0, value=";
            assertText(e, "\"gate < 0 || gate == 0 && value == 7\": " + values);
        }
    }

    void testShortCircuitCall(Boolean gate, Int value) {
        try {
            assert value < 0 || gate && isZero(value);
            assert as "expected IllegalState";
        } catch (IllegalState e) {
            String values = $"value={value}, gate={gate}, isZero(value)=";
            assertText(e, "\"value < 0 || gate && isZero(value)\": " + values);
        }

        Boolean isZero(Int number) = number == 0;
    }

    void assertText(Exception e, String expected) {
        if (e.text != expected) {
            console.print($"actual={e.text}, expected={expected}");
        }
        assert e.text == expected;
    }
}
