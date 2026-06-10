/**
 * Various types of switch statement using Char values.
 */
package charSwitchTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Char switch tests >>>>");

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

        console.print(">>>> Finished Char switch tests >>>>");
    }

    void testSimpleSwitch() {
// TODO requires Char to be compiled by the JIT for Char.toInt64()
//        assert performSimpleSwitch('a') == "one";
//        assert performSimpleSwitch('b') == "two";
//        assert performSimpleSwitch('c') == "default";
    }

//    String performSimpleSwitch(Char n) {
//        switch (n) {
//        case 'a':
//            return "one";
//        case 'b':
//            return "two";
//        default:
//            return "default";
//        }
//    }

    void testSimpleSwitchWithNullable() {
        assert performSimpleSwitchWithNullable('a') == "one";
        assert performSimpleSwitchWithNullable('b') == "two";
        assert performSimpleSwitchWithNullable('c') == "default";
        assert performSimpleSwitchWithNullable(Null) == "default";
    }

    String performSimpleSwitchWithNullable(Char? n) {
        switch (n) {
        case 'a':
            return "one";
        case 'b':
            return "two";
        default:
            return "default";
        }
    }

    void testSimpleSwitchWithNullableAndNullCase() {
        assert performSimpleSwitchWithNullableAndNullCase('a') == "one";
        assert performSimpleSwitchWithNullableAndNullCase('b') == "two";
        assert performSimpleSwitchWithNullableAndNullCase('c') == "default";
        assert performSimpleSwitchWithNullableAndNullCase(Null) == "**Null**";
    }

    String performSimpleSwitchWithNullableAndNullCase(Char? n) {
        switch (n) {
        case Null:
            return "**Null**";
        case 'a':
            return "one";
        case 'b':
            return "two";
        default:
            return "default";
        }
    }

    void testSimpleSwitchMultiCases() {
// TODO requires Char to be compiled by the JIT for Char.toInt64()
//        assert performSimpleSwitchMultiCases('a') == "one";
//        assert performSimpleSwitchMultiCases('c') == "one";
//        assert performSimpleSwitchMultiCases('e') == "one";
//        assert performSimpleSwitchMultiCases('b') == "two";
//        assert performSimpleSwitchMultiCases('d') == "two";
//        assert performSimpleSwitchMultiCases('f') == "two";
//        assert performSimpleSwitchMultiCases('g') == "default";
    }

//    String performSimpleSwitchMultiCases(Char n) {
//        switch (n) {
//        case 'a', 'c', 'e':
//            return "one";
//        case 'b', 'd', 'f':
//            return "two";
//        default:
//            return "default";
//        }
//    }

    void testSimpleSwitchMixedCases() {
// TODO requires Char to be compiled by the JIT for Char.toInt64()
//        assert performSimpleSwitchMixedCases('a') == "one";
//        assert performSimpleSwitchMixedCases('c') == "one";
//        assert performSimpleSwitchMixedCases('k') == "one";
//        assert performSimpleSwitchMixedCases('m') == "one";
//        assert performSimpleSwitchMixedCases('o') == "one";
//        assert performSimpleSwitchMixedCases('t') == "one";
//        assert performSimpleSwitchMixedCases('b') == "two";
//        assert performSimpleSwitchMixedCases('e') == "two";
//        assert performSimpleSwitchMixedCases('f') == "two";
//        assert performSimpleSwitchMixedCases('j') == "two";
//        assert performSimpleSwitchMixedCases('r') == "two";
//        assert performSimpleSwitchMixedCases('d') == "default";
//        assert performSimpleSwitchMixedCases('s') == "default";
//        assert performSimpleSwitchMixedCases('v') == "default";
    }

//    String performSimpleSwitchMixedCases(Char n) {
//        switch (n) {
//        case 'a', 'c', 'k'..'o', 't':
//            return "one";
//        case 'b', 'e'..'j', 'r':
//            return "two";
//        default:
//            return "default";
//        }
//    }

    void testSimpleSwitchNoDefault() {
// TODO requires Char to be compiled by the JIT for Char.toInt64()
//        assert performSimpleSwitchNoDefault('a') == "one";
//        assert performSimpleSwitchNoDefault('b') == "two";
//        assert performSimpleSwitchNoDefault('c') == "foo";
    }

//    String performSimpleSwitchNoDefault(Char n) {
//        switch (n) {
//        case 'a':
//            return "one";
//        case 'b':
//            return "two";
//        }
//        return "foo";
//    }

    void testSimpleSwitchWithBreak() {
// TODO requires Char to be compiled by the JIT for Char.toInt64()
//        assert performSimpleSwitchWithBreak('a') == "one";
//        assert performSimpleSwitchWithBreak('b') == "two";
//        assert performSimpleSwitchWithBreak('c') == "default";
    }

//    String performSimpleSwitchWithBreak(Char n) {
//        String s = "";
//        switch (n) {
//        case 'a':
//            s = "one";
//            break;
//        case 'b':
//            s = "two";
//            break;
//        default:
//            s = "default";
//            break;
//        }
//        return s;
//    }

    void testSimpleSwitchWithContinue() {
// TODO requires Char to be compiled by the JIT for Char.toInt64()
//        assert performSimpleSwitchWithContinue('a') == 1;
//        assert performSimpleSwitchWithContinue('b') == 110;
//        assert performSimpleSwitchWithContinue('c') == 100;
    }

//    Int performSimpleSwitchWithContinue(Char i) {
//        Int n = 0;
//        switch (i) {
//        case 'a':
//            n += 1;
//            break;
//        case 'b':
//            n += 10;
//            continue;
//        default:
//            n += 100;
//            break;
//        }
//        return n;
//    }

    void testMultiSwitch() {
        assert performMultiSwitch('a', 'b') == "one";
        assert performMultiSwitch('c', 'd') == "two";
        assert performMultiSwitch('a', 'c') == "default";
        assert performMultiSwitch('d', 'b') == "default";
    }

    String performMultiSwitch(Char n1, Char n2) {
        switch (n1, n2) {
        case ('a', 'b'):
            return "one";
        case ('c', 'd'):
            return "two";
        default:
            return "default";
        }
    }

    void testMultiSwitchWithNoDefault() {
        assert performMultiSwitchWithNoDefault('a', 'b') == "one";
        assert performMultiSwitchWithNoDefault('c', 'd') == "two";
        assert performMultiSwitchWithNoDefault('a', 'c') == "foo";
        assert performMultiSwitchWithNoDefault('d', 'b') == "foo";
    }

    String performMultiSwitchWithNoDefault(Char n1, Char n2) {
        switch (n1, n2) {
        case ('a', 'b'):
            return "one";
        case ('c', 'd'):
            return "two";
        }
        return "foo";
    }

    void testMultiSwitchWithBreak() {
        assert performMultiSwitchWithBreak('a', 'b') == "one";
        assert performMultiSwitchWithBreak('c', 'd') == "two";
        assert performMultiSwitchWithBreak('a', 'c') == "default";
        assert performMultiSwitchWithBreak('d', 'b') == "default";
    }

    String performMultiSwitchWithBreak(Char n1, Char n2) {
        String s = "";
        switch (n1, n2) {
        case ('a', 'b'):
            s = "one";
            break;
        case ('c', 'd'):
            s = "two";
            break;
        default:
            s = "default";
            break;
        }
        return s;
    }

    void testMultiSwitchWithContinue() {
        assert performMultiSwitchWithContinue('a', 'b') == 1;
        assert performMultiSwitchWithContinue('c', 'd') == 110;
        assert performMultiSwitchWithContinue('a', 'c') == 100;
        assert performMultiSwitchWithContinue('d', 'b') == 100;
    }

    Int performMultiSwitchWithContinue(Char n1, Char n2) {
        Int n = 0;
        switch (n1, n2) {
        case ('a', 'b'):
            n = n + 1;
            break;
        case ('c', 'd'):
            n = n + 10;
            continue;
        default:
            n = n + 100;
            break;
        }
        return n;
    }

    void testMultiSwitchMultiCases() {
        assert performMultiSwitchMultiCases('a', 'b') == "one";
        assert performMultiSwitchMultiCases('c', 'd') == "one";
        assert performMultiSwitchMultiCases('e', 'b') == "one";
        assert performMultiSwitchMultiCases('b', 'a') == "two";
        assert performMultiSwitchMultiCases('d', 'c') == "two";
        assert performMultiSwitchMultiCases('f', 'a') == "two";
        assert performMultiSwitchMultiCases('g', 'b') == "default";
    }

    String performMultiSwitchMultiCases(Char n1, Char n2) {
        switch (n1, n2) {
        case ('a', 'b'), ('c', 'b'), ('e', 'b'),
             ('a', 'd'), ('c', 'd'), ('e', 'd'),
             ('a', 'f'), ('c', 'f'), ('e', 'f'):
            return "one";
        case ('b', 'a'), ('d', 'a'), ('f', 'a'),
             ('b', 'c'), ('d', 'c'), ('f', 'c'),
             ('b', 'e'), ('d', 'e'), ('f', 'e'):
            return "two";
        default:
            return "default";
        }
    }

    void testMultiSwitchMultiCasesWithRanges() {
        assert performMultiSwitchMultiCasesWithRanges('a', 'b') == "one";
        assert performMultiSwitchMultiCasesWithRanges('a', 'd') == "one";
        assert performMultiSwitchMultiCasesWithRanges('a', 'g') == "one";
        assert performMultiSwitchMultiCasesWithRanges('a', 'h') == "one";
        assert performMultiSwitchMultiCasesWithRanges('e', 'b') == "one";
        assert performMultiSwitchMultiCasesWithRanges('b', 'a') == "two";
        assert performMultiSwitchMultiCasesWithRanges('d', 'c') == "two";
        assert performMultiSwitchMultiCasesWithRanges('f', 'a') == "two";
        assert performMultiSwitchMultiCasesWithRanges('g', 'b') == "default";
    }

    String performMultiSwitchMultiCasesWithRanges(Char n1, Char n2) {
        switch (n1, n2) {
        case ('a', 'b'), ('c', 'b'), ('e', 'b'),
             ('a', 'd'..'h'), ('c', 'd'..'h'), ('e', 'd'..'h'),
             ('a', 'j'), ('c', 'j'), ('e', 'j'):
            return "one";
        case ('b', 'a'), ('d', 'a'), ('f', 'a'),
             ('b', 'c'), ('d', 'c'), ('f', 'c'),
             ('b', 'e'), ('d', 'e'), ('f', 'e'):
            return "two";
        default:
            return "default";
        }
    }

    void testRangeSwitch() {
// TODO requires Char to be compiled by the JIT for Char.toInt64()
//        assert performRangeSwitch('a') == "one";
//        assert performRangeSwitch('d') == "one";
//        assert performRangeSwitch('f') == "two";
//        assert performRangeSwitch('i') == "two";
//        assert performRangeSwitch('k') == "default";
    }

//    String performRangeSwitch(Char n) {
//        String s = "a";
//        switch (s[0]) {
//        case 'a'..'e':
//            return "one";
//        case 'f'..'j':
//            return "two";
//        default:
//            return "default";
//        }
//    }
}
