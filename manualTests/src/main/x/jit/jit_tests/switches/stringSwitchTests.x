/**
 * Various types of switch statement using String values.
 */
package stringSwitchTests {
    @Inject Console console;

    void run() {
        console.print(">>>> Running String switch tests >>>>");

        testSimpleSwitch();
        testSimpleSwitchWithNullableString();
        testSimpleSwitchWithNullableStringAndNullCase();
        testMultiSwitch();
        testRangeSwitch();

        console.print(">>>> Finished String switch tests >>>>");
    }

    void testSimpleSwitch() {
        assert verifySimpleSwitch("1") == "one";
        assert verifySimpleSwitch("2") == "two";
        assert verifySimpleSwitch("3") == "default";
    }

    String verifySimpleSwitch(String s) {
        switch (s) {
        case "1":
            return "one";
        case "2":
            return "two";
        default:
            return "default";
        }
    }

    void testSimpleSwitchWithNullableString() {
        assert verifySimpleSwitchWithNullableString("1") == "one";
        assert verifySimpleSwitchWithNullableString("2") == "two";
        assert verifySimpleSwitchWithNullableString("3") == "default";
        assert verifySimpleSwitchWithNullableString(Null) == "default";
    }

    String verifySimpleSwitchWithNullableString(String? s) {
        switch (s) {
        case "1":
            return "one";
        case "2":
            return "two";
        default:
            return "default";
        }
    }

    void testSimpleSwitchWithNullableStringAndNullCase() {
        assert verifySimpleSwitchWithNullableStringAndNullCase("1") == "one";
        assert verifySimpleSwitchWithNullableStringAndNullCase("2") == "two";
        assert verifySimpleSwitchWithNullableStringAndNullCase("3") == "default";
        assert verifySimpleSwitchWithNullableStringAndNullCase(Null) == "**Null**";
    }

    String verifySimpleSwitchWithNullableStringAndNullCase(String? s) {
        switch (s) {
        case Null:
            return "**Null**";
        case "1":
            return "one";
        case "2":
            return "two";
        default:
            return "default";
        }
    }

    void testMultiSwitch() {
        assert verifyMultiSwitch("1", "2") == "one";
        assert verifyMultiSwitch("3", "4") == "two";
        assert verifyMultiSwitch("1", "3") == "default";
    }

    String verifyMultiSwitch(String s1, String s2) {
        switch (s1, s2) {
        case ("1", "2"):
            return "one";
        case ("3", "4"):
            return "two";
        default:
            return "default";
        }
    }

    void testRangeSwitch() {
        assert performRangeSwitch("aa") == "one";
        assert performRangeSwitch("af") == "one";
        assert performRangeSwitch("az") == "one";
        assert performRangeSwitch("abc") == "two";
//        assert performRangeSwitch("ba") == "three";
//        assert performRangeSwitch("bf") == "three";
//        assert performRangeSwitch("bz") == "three";
        assert performRangeSwitch("xyz") == "default";
    }

    String performRangeSwitch(String s) {
// ToDo: This will not work if the first case is a range

        switch (s) {
        case "abc":
            return "two";
        case "aa".."az":
            return "one";
// ToDo: This should work, but it blows up with multiple range cases
//        case "ba".."bz":
//            return "three";
        default:
            return "default";
        }
    }
}
