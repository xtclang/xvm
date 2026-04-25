/**
 * Various types of switch statement using Enum values.
 */
package enumSwitchTests {
    @Inject Console console;

    void run() {
        console.print(">>>> Running Enum switch tests >>>>");

        testSimpleSwitch();
        testSimpleSwitchWithNullableEnum();
        testSimpleSwitchWithNullableEnumAndNullCase();
        testMultiSwitch();

        console.print(">>>> Finished Enum switch tests >>>>");
    }

    enum Color {Red, Orange, Yellow, Green, Blue, Indigo, Violet}


    void testSimpleSwitch() {
        assert verifySimpleSwitch(Red) == "one";
        assert verifySimpleSwitch(Blue) == "two";
        assert verifySimpleSwitch(Yellow) == "default";
    }

    String verifySimpleSwitch(Color c) {
        switch (c) {
        case Red:
            return "one";
        case Blue:
            return "two";
        default:
            return "default";
        }
    }

    void testSimpleSwitchWithNullableEnum() {
        assert verifySimpleSwitchWithNullableEnum(Red) == "one";
        assert verifySimpleSwitchWithNullableEnum(Blue) == "two";
        assert verifySimpleSwitchWithNullableEnum(Yellow) == "default";
        assert verifySimpleSwitchWithNullableEnum(Null) == "default";
    }

    String verifySimpleSwitchWithNullableEnum(Color? c) {
        switch (c) {
        case Red:
            return "one";
        case Blue:
            return "two";
        default:
            return "default";
        }
    }

    void testSimpleSwitchWithNullableEnumAndNullCase() {
        assert verifySimpleSwitchWithNullableEnumAndNullCase(Red) == "one";
        assert verifySimpleSwitchWithNullableEnumAndNullCase(Blue) == "two";
        assert verifySimpleSwitchWithNullableEnumAndNullCase(Yellow) == "default";
        assert verifySimpleSwitchWithNullableEnumAndNullCase(Null) == "**Null**";
    }

    String verifySimpleSwitchWithNullableEnumAndNullCase(Color? c) {
        switch (c) {
        case Null:
            return "**Null**";
        case Red:
            return "one";
        case Blue:
            return "two";
        default:
            return "default";
        }
    }

    void testSimpleSwitchWithRange() {
        assert verifySimpleSwitchWithRange(Red) == "one";
        assert verifySimpleSwitchWithRange(Green) == "two";
        assert verifySimpleSwitchWithRange(Blue) == "two";
        assert verifySimpleSwitchWithRange(Indigo) == "two";
        assert verifySimpleSwitchWithRange(Yellow) == "default";
    }

    String verifySimpleSwitchWithRange(Color c) {
        switch (c) {
        case Red:
            return "one";
        case Green..Indigo:
            return "two";
        default:
            return "default";
        }
    }

    void testMultiSwitch() {
        assert verifyMultiSwitch(Red, Green) == "one";
        assert verifyMultiSwitch(Yellow, Blue) == "two";
        assert verifyMultiSwitch(Red, Orange) == "default";
    }

    String verifyMultiSwitch(Color c1, Color c2) {
        switch (c1, c2) {
        case (Red, Green):
            return "one";
        case (Yellow, Blue):
            return "two";
        default:
            return "default";
        }
    }
}
