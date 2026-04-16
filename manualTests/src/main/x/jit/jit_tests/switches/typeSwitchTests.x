/**
 * Various types of switch statement switching on is(_) for type matching.
 */
package typeSwitchTests {
    @Inject Console console;

    void run() {
        console.print(">>>> Running Type switch tests >>>>");

        testSimpleSwitch();
        testMultiSwitch();
        testMultiCaseSwitchWithMatchAnyCase();

        console.print(">>>> Finished Type switch tests >>>>");
    }

    enum Color {Red, Orange, Yellow, Green, Blue, Indigo, Violet}


    void testSimpleSwitch() {
        assert verifySimpleSwitch(Int:100) == "Int64";
        assert verifySimpleSwitch(Int32:100) == "Int32";
        assert verifySimpleSwitch("Foo") == "String";
        assert verifySimpleSwitch(Color.Yellow) == "default";
    }

    String verifySimpleSwitch(Object o) {
        switch (o.is(_)) {
        case Int64:
            return "Int64";
        case Int32:
            return "Int32";
        case String:
            return "String";
        default:
            return "default";
        }
    }

    void testSimpleSwitchNoDefault() {
        assert verifySimpleSwitchNoDefault(Int:100) == "Int64";
        assert verifySimpleSwitchNoDefault(Int32:100) == "Int32";
        assert verifySimpleSwitchNoDefault("Foo") == "String";
        assert verifySimpleSwitchNoDefault(Color.Yellow) == "default";
    }

    String verifySimpleSwitchNoDefault(Object o) {
        switch (o.is(_)) {
        case Int64:
            return "Int64";
        case Int32:
            return "Int32";
        case String:
            return "String";
        }
        return "default";
    }

    void testMultiSwitch() {
        assert verifyMultiSwitch("foo", "bar") == "String-String";
        assert verifyMultiSwitch(Int:100, Color.Blue) == "Int64-Color";
        assert verifyMultiSwitch("foo", Int:100) == "default";
        assert verifyMultiSwitch(Int:100, "foo") == "default";
    }

    String verifyMultiSwitch(Object o1, Object o2) {
        switch (o1.is(_), o2.is(_)) {
        case (Int64, Color):
            return "Int64-Color";
        case (String, String):
            return "String-String";
        default:
            return "default";
        }
    }

    void testMultiCaseSwitchWithMatchAnyCase() {
        assert verifyMultiCaseSwitchWithMatchAnyCase("foo", "bar") == "String-String";
        assert verifyMultiCaseSwitchWithMatchAnyCase(Int:100, Color.Blue) == "Int64-Any";
        assert verifyMultiCaseSwitchWithMatchAnyCase(Int:100, "foo") == "Int64-Any";
        assert verifyMultiCaseSwitchWithMatchAnyCase("foo", Int:100) == "Any-Int64";
        assert verifyMultiCaseSwitchWithMatchAnyCase(Color.Red, Int:100) == "Any-Int64";
        assert verifyMultiCaseSwitchWithMatchAnyCase(Color.Red, "foo") == "default";
    }

    String verifyMultiCaseSwitchWithMatchAnyCase(Object o1, Object o2) {
        switch (o1.is(_), o2.is(_)) {
        case (Int64, _):
            return "Int64-Any";
        case (_, Int64):
            return "Any-Int64";
        case (String, String):
            return "String-String";
        default:
            return "default";
        }
    }
}
