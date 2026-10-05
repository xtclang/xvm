/**
 * Tests for the a custom enum named Color.
 */
class ColorTests {

    enum Color(String text, Int rgb) {
        Red("R", 0), Green("G", 255), Blue("B", 255*255)
    }

    void run() {
        testColorCount();
        testColorNames();
        testColorValues();

        testNext();
        testPrev();
        testSkip();
        testStepsTo();

        testOrder();

        testRed();
        testGreen();
        testBlue();
    }

    void testColorCount() {
        assert Color.count == 3;
    }

    void testColorNames() {
        assert Color.names.size == 3;
        assert Color.names[0] == "Red";
        assert Color.names[1] == "Green";
        assert Color.names[2] == "Blue";
    }

    void testColorValues() {
        assert Color.values.size == 3;
        assert Color.values[0] == Red;
        assert Color.values[1] == Green;
        assert Color.values[2] == Blue;
    }

    void testNext() {
        assert Color next := Color.Red.next();
        assert next == Green;
        assert next := Color.Green.next();
        assert next == Blue;
        Boolean hasNext = Color.Blue.next();
        assert hasNext == False;

        assert Sequential s := nextAsSequential(Color.Red);
        assert s == Color.Green;
        assert s := nextAsSequential(Color.Green);
        assert s == Color.Blue;
        hasNext = nextAsSequential(Color.Blue);
        assert hasNext == False;

// TODO see nextAsEnum below
//        assert Enum e := nextAsEnum(Color.Red);
//        assert e == Color.Green;
//        assert e := nextAsEnum(Color.Green);
//        assert e == Color.Blue;
//        hasNext = nextAsEnum(Color.Blue);
//        assert hasNext == False;
    }

    conditional Sequential nextAsSequential(Sequential s) {
        return s.next();
    }

// TODO requires calls to methods on nEnum to be invokevirtual instead of invokeinterface
//    conditional Enum nextAsEnum(Enum e) {
//        return e.next();
//    }

    void testPrev() {
        Boolean hasPrev = Color.Red.prev();
        assert hasPrev == False;
        assert Color prev := Color.Green.prev();
        assert prev == Red;
        assert prev := Color.Blue.prev();
        assert prev == Green;

        hasPrev = prevAsSequential(Color.Red);
        assert hasPrev == False;
        assert Sequential s := prevAsSequential(Color.Green);
        assert s == Color.Red;
        assert s := prevAsSequential(Color.Blue);
        assert s == Color.Green;

// TODO see prevAsEnum below
//        hasPrev = prevAsEnum(Color.Red);
//        assert hasPrev == False;
//        assert Enum e := prevAsEnum(Color.Green);
//        assert e == Color.Red;
//        assert e := prevAsEnum(Color.Blue);
//        assert e == Color.Green;
    }

    conditional Sequential prevAsSequential(Sequential s) {
        return s.prev();
    }

// TODO requires calls to methods on nEnum to be invokevirtual instead of invokeinterface
//    conditional Enum prevAsEnum(Enum e) {
//        return e.next();
//    }

    void testSkip() {
        Color b = Color.Red.skip(0);
        assert b == Color.Red;
        b = Color.Red.skip(1);
        assert b == Green;
        b = Color.Red.skip(2);
        assert b == Blue;

        b = Color.Green.skip(0);
        assert b == Color.Green;
        b = Color.Green.skip(-1);
        assert b == Color.Red;
        b = Color.Green.skip(1);
        assert b == Color.Blue;

        b = Color.Blue.skip(0);
        assert b == Color.Blue;
        b = Color.Blue.skip(-1);
        assert b == Color.Green;
        b = Color.Blue.skip(-2);
        assert b == Color.Red;

        try {
            Color.Red.skip(3);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Color.Red.skip(-1);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Color.Green.skip(2);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Color.Green.skip(-2);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Color.Blue.skip(1);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Color.Blue.skip(-3);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        Sequential s = skipAsSequential(Color.Green, 1);
        assert s == Color.Blue;

        try {
            skipAsSequential(Color.Blue, -3);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

// TODO see skipAsEnum below
//        Enum e = skipAsEnum(Color.Red, 2);
//        assert e == Color.Blue;
//
//        try {
//            skipAsEnum(Color.Green, -3);
//            assert as "should have thrown OOB";
//        } catch (OutOfBounds _) {
//            // expected
//        }
    }

    Sequential skipAsSequential(Sequential s, Int steps) {
        return s.skip(steps);
    }

// TODO requires calls to methods on nEnum to be invokevirtual instead of invokeinterface
//    Enum skipAsEnum(Enum e, Int steps) {
//        return e.skip(steps);
//    }

    void testStepsTo() {
        Color r = Red;
        Color g = Green;
        Color b = Blue;

        assert Color.Red.stepsTo(Red) == 0;
        assert r.stepsTo(Green) == 1;
        assert r.stepsTo(Blue) == 2;

        assert Color.Green.stepsTo(Green) == 0;
        assert g.stepsTo(Red) == -1;
        assert g.stepsTo(Blue) == 1;

        assert Color.Blue.stepsTo(Blue) == 0;
        assert b.stepsTo(Red) == -2;
        assert b.stepsTo(Green) == -1;

        assert stepsToAsSequential(Color.Red, Color.Blue) == 2;
        assert stepsToAsSequential(Color.Blue, Color.Green) == -1;

// TODO see stepsToAsEnum below
//        assert stepsToAsEnum(Color.Red, Color.Blue) == 2;
//        assert stepsToAsEnum(Color.Blue, Color.Green) == -1;
    }

    Int stepsToAsSequential(Sequential s1, Sequential s2) {
        return s1.stepsTo(s2);
    }

// TODO requires calls to methods on nEnum to be invokevirtual instead of invokeinterface
//    Int stepsToAsEnum(Enum e1, Enum e2) {
//        return e1.stepsTo(e2);
//    }

    void testOrder() {
        assert Color.Red <=> Color.Red == Equal; // compile time computation
        Color c = Red;
        assert c <=> Green == Lesser;
    }

    void testRed() {
        assert Color.Red.name == "Red";
        assert Color.Red.ordinal == 0;
        assert Color.Red.toString() == "Red";

        assert Color.Red.text == "R";
        assert Color.Red.rgb == 0;

        assert Color.Red.estimateStringLength() == 3;

        StringBuffer buf = new StringBuffer();
        String       s   = Color.Red.appendTo(buf).toString();
        assert s == "Red";
    }

    void testGreen() {
        assert Color.Green.name == "Green";
        assert Color.Green.ordinal == 1;
        assert Color.Green.toString() == "Green";

        assert Color.Green.text == "G";
        assert Color.Green.rgb == 255;

        assert Color.Green.estimateStringLength() == 5;

        StringBuffer buf = new StringBuffer();
        String       s   = Color.Green.appendTo(buf).toString();
        assert s == "Green";
    }

    void testBlue() {
        assert Color.Blue.name == "Blue";
        assert Color.Blue.ordinal == 2;
        assert Color.Blue.toString() == "Blue";

        assert Color.Blue.text == "B";
        assert Color.Blue.rgb == 255*255;

        assert Color.Blue.estimateStringLength() == 4;

        StringBuffer buf = new StringBuffer();
        String       s   = Color.Blue.appendTo(buf).toString();
        assert s == "Blue";
    }
}
