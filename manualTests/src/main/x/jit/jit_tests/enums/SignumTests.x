/**
 * Tests for the Number.Signum enum.
 */
class SignumTests {

    void run() {
        testSignumCount();
        testSignumNames();
        testSignumValues();

        testNext();
        testPrev();
        testSkip();
        testStepsTo();

        testNegative();
        testZero();
        testPositive();
    }

    void testSignumCount() {
        assert Signum.count == 3;
    }

    void testSignumNames() {
        assert Signum.names.size == 3;
        assert Signum.names[0] == "Negative";
        assert Signum.names[1] == "Zero";
        assert Signum.names[2] == "Positive";
    }

    void testSignumValues() {
        assert Signum.values.size == 3;
        assert Signum.values[0] == Negative;
        assert Signum.values[1] == Zero;
        assert Signum.values[2] == Positive;
    }

    void testNext() {
        assert Signum next := Signum.Negative.next();
        assert next == Zero;
        assert next := Signum.Zero.next();
        assert next == Positive;
        Boolean hasNext = Signum.Positive.next();
        assert hasNext == False;

        assert Sequential s := nextAsSequential(Signum.Negative);
        assert s == Signum.Zero;
        assert s := nextAsSequential(Signum.Zero);
        assert s == Signum.Positive;
        hasNext = nextAsSequential(Signum.Positive);
        assert hasNext == False;

// TODO see nextAsEnum below
//        assert Enum e := nextAsEnum(Signum.Negative);
//        assert e == Signum.Zero;
//        assert e := nextAsEnum(Signum.Zero);
//        assert e == Signum.Positive;
//        hasNext = nextAsEnum(Signum.Positive);
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
        Boolean hasPrev = Signum.Negative.prev();
        assert hasPrev == False;
        assert Signum prev := Signum.Zero.prev();
        assert prev == Negative;
        assert prev := Signum.Positive.prev();
        assert prev == Zero;

        hasPrev = prevAsSequential(Signum.Negative);
        assert hasPrev == False;
        assert Sequential s := prevAsSequential(Signum.Zero);
        assert s == Signum.Negative;
        assert s := prevAsSequential(Signum.Positive);
        assert s == Signum.Zero;

// TODO see prevAsEnum below
//        hasPrev = prevAsEnum(Signum.Negative);
//        assert hasPrev == False;
//        assert Enum e := prevAsEnum(Signum.Zero);
//        assert e == Signum.Negative;
//        assert e := prevAsEnum(Signum.Positive);
//        assert e == Signum.Zero;
    }

    conditional Sequential prevAsSequential(Sequential s) {
        return s.prev();
    }

// TODO requires calls to methods on nEnum to be invokevirtual instead of invokeinterface
//    conditional Enum prevAsEnum(Enum e) {
//        return e.next();
//    }

    void testSkip() {
        Signum b = Signum.Negative.skip(0);
        assert b == Signum.Negative;
        b = Signum.Negative.skip(1);
        assert b == Zero;
        b = Signum.Negative.skip(2);
        assert b == Positive;

        b = Signum.Zero.skip(0);
        assert b == Signum.Zero;
        b = Signum.Zero.skip(-1);
        assert b == Signum.Negative;
        b = Signum.Zero.skip(1);
        assert b == Signum.Positive;

        b = Signum.Positive.skip(0);
        assert b == Signum.Positive;
        b = Signum.Positive.skip(-1);
        assert b == Signum.Zero;
        b = Signum.Positive.skip(-2);
        assert b == Signum.Negative;

        try {
            Signum.Negative.skip(3);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Signum.Negative.skip(-1);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Signum.Zero.skip(2);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Signum.Zero.skip(-2);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Signum.Positive.skip(1);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Signum.Positive.skip(-3);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        Sequential s = skipAsSequential(Signum.Zero, 1);
        assert s == Signum.Positive;

        try {
            skipAsSequential(Signum.Positive, -3);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

// TODO see skipAsEnum below
//        Enum e = skipAsEnum(Signum.Negative, 2);
//        assert e == Signum.Positive;
//
//        try {
//            skipAsEnum(Signum.Zero, -3);
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
        Signum n = Negative;
        Signum z = Zero;
        Signum p = Positive;

        assert Signum.Negative.stepsTo(Negative) == 0;
        assert n.stepsTo(Zero) == 1;
        assert n.stepsTo(Positive) == 2;

        assert Signum.Zero.stepsTo(Zero) == 0;
        assert z.stepsTo(Negative) == -1;
        assert z.stepsTo(Positive) == 1;

        assert Signum.Positive.stepsTo(Positive) == 0;
        assert p.stepsTo(Negative) == -2;
        assert p.stepsTo(Zero) == -1;

        assert stepsToAsSequential(Signum.Negative, Signum.Positive) == 2;
        assert stepsToAsSequential(Signum.Positive, Signum.Zero) == -1;

// TODO see stepsToAsEnum below
//        assert stepsToAsEnum(Signum.Negative, Signum.Positive) == 2;
//        assert stepsToAsEnum(Signum.Positive, Signum.Zero) == -1;
    }

    Int stepsToAsSequential(Sequential s1, Sequential s2) {
        return s1.stepsTo(s2);
    }

// TODO requires calls to methods on nEnum to be invokevirtual instead of invokeinterface
//    Int stepsToAsEnum(Enum e1, Enum e2) {
//        return e1.stepsTo(e2);
//    }

    void testNegative() {
        assert Signum.Negative.name == "Negative";
        assert Signum.Negative.ordinal == 0;
        assert Signum.Negative.toString() == "Negative";

        assert Signum.Negative.prefix == "-";
        assert Signum.Negative.factor == -1;
        assert Signum.Negative.ordered == Lesser;

        assert Signum.Negative.estimateStringLength() == 8;

        StringBuffer buf = new StringBuffer();
        String       s   = Signum.Negative.appendTo(buf).toString();
        assert s == "Negative";
    }

    void testZero() {
        assert Signum.Zero.name == "Zero";
        assert Signum.Zero.ordinal == 1;
        assert Signum.Zero.toString() == "Zero";

        assert Signum.Zero.prefix == "";
        assert Signum.Zero.factor == 0;
        assert Signum.Zero.ordered == Equal;

        assert Signum.Zero.estimateStringLength() == 4;

        StringBuffer buf = new StringBuffer();
        String       s   = Signum.Zero.appendTo(buf).toString();
        assert s == "Zero";
    }

    void testPositive() {
        assert Signum.Positive.name == "Positive";
        assert Signum.Positive.ordinal == 2;
        assert Signum.Positive.toString() == "Positive";

        assert Signum.Positive.prefix == "+";
        assert Signum.Positive.factor == +1;
        assert Signum.Positive.ordered == Greater;

        assert Signum.Positive.estimateStringLength() == 8;

        StringBuffer buf = new StringBuffer();
        String       s   = Signum.Positive.appendTo(buf).toString();
        assert s == "Positive";
    }
}
