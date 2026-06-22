package assertTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Assert Message Tests >>>>");

        testPrimitiveValues(7, 8);
        testNullNullablePrimitive(Null, False);
        testXvmPrimitiveValues(1234567890, 9876543210);
        testNullNullableXvmPrimitive(Null, False);
        testStringValue("world", 7);

        console.print("<<<< Finished Assert Message Tests <<<<");
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
            assertText(e, "\"value != Null || value2\": value=Null, value2=");
        }
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
            assertText(e, "\"value != Null || value2\": value=Null, value2=");
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

    void assertText(Exception e, String expected) {
        if (e.text != expected) {
            console.print($"actual={e.text}, expected={expected}");
        }
        assert e.text == expected;
    }
}
