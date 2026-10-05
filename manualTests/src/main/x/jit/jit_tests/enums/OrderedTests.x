/**
 * Tests for the Ordered enum.
 */
class OrderedTests {

    void run() {
        testOrderedCount();
        testOrderedNames();
        testOrderedValues();

        testNext();
        testPrev();
        testSkip();
        testStepsTo();

        testLesser();
        testEqual();
        testGreater();
    }

    void testOrderedCount() {
        assert Ordered.count == 3;
    }

    void testOrderedNames() {
        assert Ordered.names.size == 3;
        assert Ordered.names[0] == "Lesser";
        assert Ordered.names[1] == "Equal";
        assert Ordered.names[2] == "Greater";
    }

    void testOrderedValues() {
        assert Ordered.values.size == 3;
        assert Ordered.values[0] == Lesser;
        assert Ordered.values[1] == Equal;
        assert Ordered.values[2] == Greater;
    }

    void testNext() {
        assert Ordered next := Ordered.Lesser.next();
        assert next == Equal;
        assert next := Ordered.Equal.next();
        assert next == Greater;
        Boolean hasNext = Ordered.Greater.next();
        assert hasNext == False;

        assert Sequential s := nextAsSequential(Ordered.Lesser);
        assert s == Ordered.Equal;
        assert s := nextAsSequential(Ordered.Equal);
        assert s == Ordered.Greater;
        hasNext = nextAsSequential(Ordered.Greater);
        assert hasNext == False;

// TODO see nextAsEnum below
//        assert Enum e := nextAsEnum(Ordered.Lesser);
//        assert e == Ordered.Equal;
//        assert e := nextAsEnum(Ordered.Equal);
//        assert e == Ordered.Greater;
//        hasNext = nextAsEnum(Ordered.Greater);
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
        Boolean hasPrev = Ordered.Lesser.prev();
        assert hasPrev == False;
        assert Ordered prev := Ordered.Equal.prev();
        assert prev == Lesser;
        assert prev := Ordered.Greater.prev();
        assert prev == Equal;

        hasPrev = prevAsSequential(Ordered.Lesser);
        assert hasPrev == False;
        assert Sequential s := prevAsSequential(Ordered.Equal);
        assert s == Ordered.Lesser;
        assert s := prevAsSequential(Ordered.Greater);
        assert s == Ordered.Equal;

// TODO see prevAsEnum below
//        hasPrev = prevAsEnum(Ordered.Lesser);
//        assert hasPrev == False;
//        assert Enum e := prevAsEnum(Ordered.Equal);
//        assert e == Ordered.Lesser;
//        assert e := prevAsEnum(Ordered.Greater);
//        assert e == Ordered.Equal;
    }

    conditional Sequential prevAsSequential(Sequential s) {
        return s.prev();
    }

// TODO requires calls to methods on nEnum to be invokevirtual instead of invokeinterface
//    conditional Enum prevAsEnum(Enum e) {
//        return e.next();
//    }

    void testSkip() {
        Ordered b = Ordered.Lesser.skip(0);
        assert b == Ordered.Lesser;
        b = Ordered.Lesser.skip(1);
        assert b == Equal;
        b = Ordered.Lesser.skip(2);
        assert b == Greater;

        b = Ordered.Equal.skip(0);
        assert b == Ordered.Equal;
        b = Ordered.Equal.skip(-1);
        assert b == Ordered.Lesser;
        b = Ordered.Equal.skip(1);
        assert b == Ordered.Greater;

        b = Ordered.Greater.skip(0);
        assert b == Ordered.Greater;
        b = Ordered.Greater.skip(-1);
        assert b == Ordered.Equal;
        b = Ordered.Greater.skip(-2);
        assert b == Ordered.Lesser;

        try {
            Ordered.Lesser.skip(3);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Ordered.Lesser.skip(-1);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Ordered.Equal.skip(2);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Ordered.Equal.skip(-2);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Ordered.Greater.skip(1);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Ordered.Greater.skip(-3);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        Sequential s = skipAsSequential(Ordered.Equal, 1);
        assert s == Ordered.Greater;

        try {
            skipAsSequential(Ordered.Greater, -3);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

// TODO see skipAsEnum below
//        Enum e = skipAsEnum(Ordered.Lesser, 2);
//        assert e == Ordered.Greater;
//
//        try {
//            skipAsEnum(Ordered.Equal, -3);
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
        Ordered l = Lesser;
        Ordered e = Equal;
        Ordered g = Greater;

        assert Ordered.Lesser.stepsTo(Lesser) == 0;
        assert l.stepsTo(Equal) == 1;
        assert l.stepsTo(Greater) == 2;

        assert Ordered.Equal.stepsTo(Equal) == 0;
        assert e.stepsTo(Lesser) == -1;
        assert e.stepsTo(Greater) == 1;

        assert Ordered.Greater.stepsTo(Greater) == 0;
        assert g.stepsTo(Lesser) == -2;
        assert g.stepsTo(Equal) == -1;

        assert stepsToAsSequential(Ordered.Lesser, Ordered.Greater) == 2;
        assert stepsToAsSequential(Ordered.Greater, Ordered.Equal) == -1;

// TODO see stepsToAsEnum below
//        assert stepsToAsEnum(Ordered.Lesser, Ordered.Greater) == 2;
//        assert stepsToAsEnum(Ordered.Greater, Ordered.Equal) == -1;
    }

    Int stepsToAsSequential(Sequential s1, Sequential s2) {
        return s1.stepsTo(s2);
    }

// TODO requires calls to methods on nEnum to be invokevirtual instead of invokeinterface
//    Int stepsToAsEnum(Enum e1, Enum e2) {
//        return e1.stepsTo(e2);
//    }

    void testLesser() {
        assert Ordered.Lesser.name == "Lesser";
        assert Ordered.Lesser.ordinal == 0;
        assert Ordered.Lesser.toString() == "Lesser";

        assert Ordered.Lesser.symbol == "<";
        assert Ordered.Lesser.reversed == Greater;

        assert Ordered.Lesser.estimateStringLength() == 6;

        StringBuffer buf = new StringBuffer();
        String       s   = Ordered.Lesser.appendTo(buf).toString();
        assert s == "Lesser";
    }

    void testEqual() {
        assert Ordered.Equal.name == "Equal";
        assert Ordered.Equal.ordinal == 1;
        assert Ordered.Equal.toString() == "Equal";

        assert Ordered.Equal.symbol == "=";
        assert Ordered.Equal.reversed == Equal;

        assert Ordered.Equal.estimateStringLength() == 5;

        StringBuffer buf = new StringBuffer();
        String       s   = Ordered.Equal.appendTo(buf).toString();
        assert s == "Equal";
    }

    void testGreater() {
        assert Ordered.Greater.name == "Greater";
        assert Ordered.Greater.ordinal == 2;
        assert Ordered.Greater.toString() == "Greater";

        assert Ordered.Greater.symbol == ">";
        assert Ordered.Greater.reversed == Lesser;

        assert Ordered.Greater.estimateStringLength() == 7;

        StringBuffer buf = new StringBuffer();
        String       s   = Ordered.Greater.appendTo(buf).toString();
        assert s == "Greater";
    }
}
