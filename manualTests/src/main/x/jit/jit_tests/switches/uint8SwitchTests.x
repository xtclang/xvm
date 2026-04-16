/**
 * Various types of switch statement using Uint8 values.
 */
package uint8SwitchTests {
    @Inject Console console;

    void run() {
        console.print(">>>> Running UInt8 switch tests >>>>");

        testSimpleSwitch();
        testSimpleSwitchWithNullable();
        testSimpleSwitchWithNullableAndNullCase();
        testSimpleSwitchMultiCases();
        testSimpleSwitchMixedCases();
        testSimpleSwitchNoDefault();
        testSimpleSwitchWithBreak();
        testSimpleSwitchWithContinue();

        testMultiSwitch();
        testMultiSwitchWithNoDefault();
        testMultiSwitchWithBreak();
        testMultiSwitchWithContinue();
        testMultiSwitchMultiCases();
        testMultiSwitchMultiCasesWithRanges();

        testRangeSwitch();

        console.print(">>>> Finished UInt8 switch tests >>>>");
    }

    void testSimpleSwitch() {
        assert performSimpleSwitch(1) == "one";
        assert performSimpleSwitch(2) == "two";
        assert performSimpleSwitch(3) == "default";
    }

    String performSimpleSwitch(UInt8 n) {
        switch (n) {
        case 1:
            return "one";
        case 2:
            return "two";
        default:
            return "default";
        }
    }

    void testSimpleSwitchWithNullable() {
        assert performSimpleSwitchWithNullable(1) == "one";
        assert performSimpleSwitchWithNullable(2) == "two";
        assert performSimpleSwitchWithNullable(3) == "default";
        assert performSimpleSwitchWithNullable(Null) == "default";
    }

    String performSimpleSwitchWithNullable(UInt8? n) {
        switch (n) {
        case 1:
            return "one";
        case 2:
            return "two";
        default:
            return "default";
        }
    }

    void testSimpleSwitchWithNullableAndNullCase() {
        assert performSimpleSwitchWithNullableAndNullCase(1) == "one";
        assert performSimpleSwitchWithNullableAndNullCase(2) == "two";
        assert performSimpleSwitchWithNullableAndNullCase(3) == "default";
        assert performSimpleSwitchWithNullableAndNullCase(Null) == "**Null**";
    }

    String performSimpleSwitchWithNullableAndNullCase(UInt8? n) {
        switch (n) {
        case Null:
            return "**Null**";
        case 1:
            return "one";
        case 2:
            return "two";
        default:
            return "default";
        }
    }

    void testSimpleSwitchMultiCases() {
        assert performSimpleSwitchMultiCases(1) == "one";
        assert performSimpleSwitchMultiCases(3) == "one";
        assert performSimpleSwitchMultiCases(5) == "one";
        assert performSimpleSwitchMultiCases(2) == "two";
        assert performSimpleSwitchMultiCases(4) == "two";
        assert performSimpleSwitchMultiCases(6) == "two";
        assert performSimpleSwitchMultiCases(7) == "default";
    }

    String performSimpleSwitchMultiCases(UInt8 n) {
        switch (n) {
        case 1, 3, 5:
            return "one";
        case 2, 4, 6:
            return "two";
        default:
            return "default";
        }
    }

    void testSimpleSwitchMixedCases() {
        assert performSimpleSwitchMixedCases(1) == "one";
        assert performSimpleSwitchMixedCases(3) == "one";
        assert performSimpleSwitchMixedCases(11) == "one";
        assert performSimpleSwitchMixedCases(13) == "one";
        assert performSimpleSwitchMixedCases(15) == "one";
        assert performSimpleSwitchMixedCases(20) == "one";
        assert performSimpleSwitchMixedCases(2) == "two";
        assert performSimpleSwitchMixedCases(5) == "two";
        assert performSimpleSwitchMixedCases(6) == "two";
        assert performSimpleSwitchMixedCases(10) == "two";
        assert performSimpleSwitchMixedCases(18) == "two";
        assert performSimpleSwitchMixedCases(4) == "default";
        assert performSimpleSwitchMixedCases(19) == "default";
        assert performSimpleSwitchMixedCases(22) == "default";
    }

    String performSimpleSwitchMixedCases(UInt8 n) {
        switch (n) {
        case 1, 3, 11..15, 20:
            return "one";
        case 2, 5..10, 18:
            return "two";
        default:
            return "default";
        }
    }

    void testSimpleSwitchNoDefault() {
        assert performSimpleSwitchNoDefault(1) == "one";
        assert performSimpleSwitchNoDefault(2) == "two";
        assert performSimpleSwitchNoDefault(3) == "foo";
    }

    String performSimpleSwitchNoDefault(UInt8 n) {
        switch (n) {
        case 1:
            return "one";
        case 2:
            return "two";
        }
        return "foo";
    }

    void testSimpleSwitchWithBreak() {
        assert performSimpleSwitchWithBreak(1) == "one";
        assert performSimpleSwitchWithBreak(2) == "two";
        assert performSimpleSwitchWithBreak(3) == "default";
    }

    String performSimpleSwitchWithBreak(UInt8 n) {
        String s = "";
        switch (n) {
        case 1:
            s = "one";
            break;
        case 2:
            s = "two";
            break;
        default:
            s = "default";
            break;
        }
        return s;
    }

    void testSimpleSwitchWithContinue() {
        assert performSimpleSwitchWithContinue(1) == 1;
        assert performSimpleSwitchWithContinue(2) == 110;
        assert performSimpleSwitchWithContinue(3) == 100;
    }

    Int performSimpleSwitchWithContinue(UInt8 i) {
        Int n = 0;
        switch (i) {
        case 1:
            n += 1;
            break;
        case 2:
            n += 10;
            continue;
        default:
            n += 100;
            break;
        }
        return n;
    }

    void testMultiSwitch() {
        assert performMultiSwitch(1, 2) == "one";
        assert performMultiSwitch(3, 4) == "two";
        assert performMultiSwitch(1, 3) == "default";
        assert performMultiSwitch(4, 2) == "default";
    }

    String performMultiSwitch(UInt8 n1, UInt8 n2) {
        switch (n1, n2) {
        case (1, 2):
            return "one";
        case (3, 4):
            return "two";
        default:
            return "default";
        }
    }

    void testMultiSwitchWithNoDefault() {
        assert performMultiSwitchWithNoDefault(1, 2) == "one";
        assert performMultiSwitchWithNoDefault(3, 4) == "two";
        assert performMultiSwitchWithNoDefault(1, 3) == "foo";
        assert performMultiSwitchWithNoDefault(4, 2) == "foo";
    }

    String performMultiSwitchWithNoDefault(UInt8 n1, UInt8 n2) {
        switch (n1, n2) {
        case (1, 2):
            return "one";
        case (3, 4):
            return "two";
        }
        return "foo";
    }

    void testMultiSwitchWithBreak() {
        assert performMultiSwitchWithBreak(1, 2) == "one";
        assert performMultiSwitchWithBreak(3, 4) == "two";
        assert performMultiSwitchWithBreak(1, 3) == "default";
        assert performMultiSwitchWithBreak(4, 2) == "default";
    }

    String performMultiSwitchWithBreak(UInt8 n1, UInt8 n2) {
        String s = "";
        switch (n1, n2) {
        case (1, 2):
            s = "one";
            break;
        case (3, 4):
            s = "two";
            break;
        default:
            s = "default";
            break;
        }
        return s;
    }

    void testMultiSwitchWithContinue() {
        assert performMultiSwitchWithContinue(1, 2) == 1;
        assert performMultiSwitchWithContinue(3, 4) == 110;
        assert performMultiSwitchWithContinue(1, 3) == 100;
        assert performMultiSwitchWithContinue(4, 2) == 100;
    }

    Int performMultiSwitchWithContinue(UInt8 n1, UInt8 n2) {
        Int n = 0;
        switch (n1, n2) {
        case (1, 2):
            n = n + 1;
            break;
        case (3, 4):
            n = n + 10;
            continue;
        default:
            n = n + 100;
            break;
        }
        return n;
    }

    void testMultiSwitchMultiCases() {
        assert performMultiSwitchMultiCases(1, 2) == "one";
        assert performMultiSwitchMultiCases(3, 4) == "one";
        assert performMultiSwitchMultiCases(5, 2) == "one";
        assert performMultiSwitchMultiCases(2, 1) == "two";
        assert performMultiSwitchMultiCases(4, 3) == "two";
        assert performMultiSwitchMultiCases(6, 1) == "two";
        assert performMultiSwitchMultiCases(7, 2) == "default";
    }

    String performMultiSwitchMultiCases(UInt8 n1, UInt8 n2) {
        switch (n1, n2) {
        case (1, 2), (3, 2), (5, 2),
             (1, 4), (3, 4), (5, 4),
             (1, 6), (3, 6), (5, 6):
            return "one";
        case (2, 1), (4, 1), (6, 1),
             (2, 3), (4, 3), (6, 3),
             (2, 5), (4, 5), (6, 5):
            return "two";
        default:
            return "default";
        }
    }

    void testMultiSwitchMultiCasesWithRanges() {
        assert performMultiSwitchMultiCasesWithRanges(1, 2) == "one";
        assert performMultiSwitchMultiCasesWithRanges(1, 4) == "one";
        assert performMultiSwitchMultiCasesWithRanges(1, 7) == "one";
        assert performMultiSwitchMultiCasesWithRanges(1, 8) == "one";
        assert performMultiSwitchMultiCasesWithRanges(5, 2) == "one";
        assert performMultiSwitchMultiCasesWithRanges(2, 1) == "two";
        assert performMultiSwitchMultiCasesWithRanges(4, 3) == "two";
        assert performMultiSwitchMultiCasesWithRanges(6, 1) == "two";
        assert performMultiSwitchMultiCasesWithRanges(7, 2) == "default";
    }

    String performMultiSwitchMultiCasesWithRanges(UInt8 n1, UInt8 n2) {
        switch (n1, n2) {
        case (1, 2), (3, 2), (5, 2),
             (1, 4..8), (3, 4..8), (5, 4..8),
             (1, 10), (3, 10), (5, 10):
            return "one";
        case (2, 1), (4, 1), (6, 1),
             (2, 3), (4, 3), (6, 3),
             (2, 5), (4, 5), (6, 5):
            return "two";
        default:
            return "default";
        }
    }

    void testRangeSwitch() {
        assert performRangeSwitch(1) == "one";
        assert performRangeSwitch(4) == "one";
        assert performRangeSwitch(6) == "two";
        assert performRangeSwitch(9) == "two";
        assert performRangeSwitch(11) == "default";
    }

    String performRangeSwitch(UInt8 n) {
        switch (n) {
        case 1..5:
            return "one";
        case 6..10:
            return "two";
        default:
            return "default";
        }
    }
}
