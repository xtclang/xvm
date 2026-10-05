import ecstasy.numbers.FPNumber.Rounding;

/**
 * Tests for the FPNumber.Rounding enum.
 */
class RoundingTests {

    void run() {
        testRoundingCount();
        testRoundingNames();
        testRoundingValues();

        testNext();
        testPrev();
        testSkip();
        testStepsTo();

        testTiesToEven();
        testTiesToAway();
        testTowardPositive();
        testTowardZero();
        testTowardNegative();
    }

    void testRoundingCount() {
        assert Rounding.count == 5;
    }

    void testRoundingNames() {
        assert Rounding.names.size == 5;
        assert Rounding.names[0] == "TiesToEven";
        assert Rounding.names[1] == "TiesToAway";
        assert Rounding.names[2] == "TowardPositive";
        assert Rounding.names[3] == "TowardZero";
        assert Rounding.names[4] == "TowardNegative";
    }

    void testRoundingValues() {
        assert Rounding.values.size == 5;
        assert Rounding.values[0] == TiesToEven;
        assert Rounding.values[1] == TiesToAway;
        assert Rounding.values[2] == TowardPositive;
        assert Rounding.values[3] == TowardZero;
        assert Rounding.values[4] == TowardNegative;
    }

    void testNext() {
        assert Rounding next := Rounding.TiesToEven.next();
        assert next == TiesToAway;
        assert next := Rounding.TiesToAway.next();
        assert next == TowardPositive;
        assert next := Rounding.TowardPositive.next();
        assert next == TowardZero;
        assert next := Rounding.TowardZero.next();
        assert next == TowardNegative;
        Boolean hasNext = Rounding.TowardNegative.next();
        assert hasNext == False;

        assert Sequential s := nextAsSequential(Rounding.TiesToEven);
        assert s == Rounding.TiesToAway;
        assert s := nextAsSequential(Rounding.TiesToAway);
        assert s == Rounding.TowardPositive;
        assert s := nextAsSequential(Rounding.TowardPositive);
        assert s == Rounding.TowardZero;
        assert s := nextAsSequential(Rounding.TowardZero);
        assert s == Rounding.TowardNegative;
        hasNext = nextAsSequential(Rounding.TowardNegative);
        assert hasNext == False;

// TODO see nextAsEnum below
//        assert Enum e := nextAsEnum(Rounding.TiesToEven);
//        assert e == Rounding.TiesToAway;
//        assert e := nextAsEnum(Rounding.TiesToAway);
//        assert e == Rounding.TowardPositive;
//        assert e := nextAsEnum(Rounding.TowardPositive);
//        assert e == Rounding.TowardZero;
//        assert e := nextAsEnum(Rounding.TowardZero);
//        assert e == Rounding.TowardNegative;
//        hasNext = nextAsEnum(Rounding.TowardNegative);
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
        Boolean hasPrev = Rounding.TiesToEven.prev();
        assert hasPrev == False;
        assert Rounding prev := Rounding.TiesToAway.prev();
        assert prev == TiesToEven;
        assert prev := Rounding.TowardPositive.prev();
        assert prev == TiesToAway;
        assert prev := Rounding.TowardZero.prev();
        assert prev == TowardPositive;
        assert prev := Rounding.TowardNegative.prev();
        assert prev == TowardZero;

        hasPrev = prevAsSequential(Rounding.TiesToEven);
        assert hasPrev == False;
        assert Sequential s := prevAsSequential(Rounding.TiesToAway);
        assert s == Rounding.TiesToEven;
        assert s := prevAsSequential(Rounding.TowardPositive);
        assert s == Rounding.TiesToAway;
        assert s := prevAsSequential(Rounding.TowardZero);
        assert s == Rounding.TowardPositive;
        assert s := prevAsSequential(Rounding.TowardNegative);
        assert s == Rounding.TowardZero;

// TODO see prevAsEnum below
//        hasPrev = prevAsEnum(Rounding.TiesToEven);
//        assert hasPrev == False;
//        assert Enum e := prevAsEnum(Rounding.TiesToAway);
//        assert e == Rounding.TiesToEven;
//        assert e := prevAsEnum(Rounding.TowardPositive);
//        assert e == Rounding.TiesToAway;
//        assert e := prevAsEnum(Rounding.TowardZero);
//        assert e == Rounding.TowardPositive;
//        assert e := prevAsEnum(Rounding.TowardNegative);
//        assert e == Rounding.TowardZero;
    }

    conditional Sequential prevAsSequential(Sequential s) {
        return s.prev();
    }

// TODO requires calls to methods on nEnum to be invokevirtual instead of invokeinterface
//    conditional Enum prevAsEnum(Enum e) {
//        return e.next();
//    }

    void testSkip() {
        Rounding b = Rounding.TiesToEven.skip(0);
        assert b == Rounding.TiesToEven;
        b = Rounding.TiesToEven.skip(1);
        assert b == TiesToAway;
        b = Rounding.TiesToEven.skip(2);
        assert b == TowardPositive;
        b = Rounding.TiesToEven.skip(3);
        assert b == TowardZero;
        b = Rounding.TiesToEven.skip(4);
        assert b == TowardNegative;

        b = Rounding.TiesToAway.skip(0);
        assert b == Rounding.TiesToAway;
        b = Rounding.TiesToAway.skip(-1);
        assert b == Rounding.TiesToEven;
        b = Rounding.TiesToAway.skip(1);
        assert b == Rounding.TowardPositive;
        b = Rounding.TiesToAway.skip(2);
        assert b == TowardZero;
        b = Rounding.TiesToAway.skip(3);
        assert b == TowardNegative;

        b = Rounding.TowardPositive.skip(0);
        assert b == Rounding.TowardPositive;
        b = Rounding.TowardPositive.skip(-2);
        assert b == Rounding.TiesToEven;
        b = Rounding.TowardPositive.skip(-1);
        assert b == Rounding.TiesToAway;
        b = Rounding.TowardPositive.skip(1);
        assert b == TowardZero;
        b = Rounding.TowardPositive.skip(2);
        assert b == TowardNegative;

        b = Rounding.TowardZero.skip(0);
        assert b == Rounding.TowardZero;
        b = Rounding.TowardZero.skip(-3);
        assert b == Rounding.TiesToEven;
        b = Rounding.TowardZero.skip(-2);
        assert b == Rounding.TiesToAway;
        b = Rounding.TowardZero.skip(-1);
        assert b == TowardPositive;
        b = Rounding.TowardZero.skip(1);
        assert b == TowardNegative;

        b = Rounding.TowardNegative.skip(0);
        assert b == Rounding.TowardNegative;
        b = Rounding.TowardNegative.skip(-4);
        assert b == Rounding.TiesToEven;
        b = Rounding.TowardNegative.skip(-3);
        assert b == Rounding.TiesToAway;
        b = Rounding.TowardNegative.skip(-2);
        assert b == TowardPositive;
        b = Rounding.TowardNegative.skip(-1);
        assert b == TowardZero;

        try {
            Rounding.TiesToEven.skip(5);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Rounding.TiesToEven.skip(-1);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Rounding.TiesToAway.skip(4);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Rounding.TiesToAway.skip(-2);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Rounding.TowardPositive.skip(3);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Rounding.TowardPositive.skip(-5);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        Sequential s = skipAsSequential(Rounding.TiesToAway, 1);
        assert s == Rounding.TowardPositive;

        try {
            skipAsSequential(Rounding.TiesToAway, -2);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

// TODO see skipAsEnum below
//        Enum e = skipAsEnum(Rounding.TiesToEven, 2);
//        assert e == Rounding.TowardPositive;
//
//        try {
//            skipAsEnum(Rounding.TiesToAway, -3);
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
        Rounding e = TiesToEven;
        Rounding a = TiesToAway;
        Rounding p = TowardPositive;
        Rounding z = TowardZero;
        Rounding n = TowardNegative;

        assert Rounding.TiesToEven.stepsTo(TiesToEven) == 0;
        assert e.stepsTo(TiesToAway) == 1;
        assert e.stepsTo(TowardPositive) == 2;
        assert e.stepsTo(TowardZero) == 3;
        assert e.stepsTo(TowardNegative) == 4;

        assert Rounding.TiesToAway.stepsTo(TiesToAway) == 0;
        assert a.stepsTo(TiesToEven) == -1;
        assert a.stepsTo(TowardPositive) == 1;
        assert a.stepsTo(TowardZero) == 2;
        assert a.stepsTo(TowardNegative) == 3;

        assert Rounding.TowardPositive.stepsTo(TowardPositive) == 0;
        assert p.stepsTo(TiesToEven) == -2;
        assert p.stepsTo(TiesToAway) == -1;
        assert p.stepsTo(TowardZero) == 1;
        assert p.stepsTo(TowardNegative) == 2;

        assert Rounding.TowardZero.stepsTo(TowardZero) == 0;
        assert z.stepsTo(TiesToEven) == -3;
        assert z.stepsTo(TiesToAway) == -2;
        assert z.stepsTo(TowardPositive) == -1;
        assert z.stepsTo(TowardNegative) == 1;

        assert Rounding.TowardNegative.stepsTo(TowardNegative) == 0;
        assert n.stepsTo(TiesToEven) == -4;
        assert n.stepsTo(TiesToAway) == -3;
        assert n.stepsTo(TowardPositive) == -2;
        assert n.stepsTo(TowardZero) == -1;

        assert stepsToAsSequential(Rounding.TiesToEven, Rounding.TowardPositive) == 2;
        assert stepsToAsSequential(Rounding.TowardPositive, Rounding.TiesToAway) == -1;

// TODO see stepsToAsEnum below
//        assert stepsToAsEnum(Rounding.TiesToEven, Rounding.TowardPositive) == 2;
//        assert stepsToAsEnum(Rounding.TowardPositive, Rounding.TiesToAway) == -1;
    }

    Int stepsToAsSequential(Sequential s1, Sequential s2) {
        return s1.stepsTo(s2);
    }

// TODO requires calls to methods on nEnum to be invokevirtual instead of invokeinterface
//    Int stepsToAsEnum(Enum e1, Enum e2) {
//        return e1.stepsTo(e2);
//    }

    void testTiesToEven() {
        assert Rounding.TiesToEven.name == "TiesToEven";
        assert Rounding.TiesToEven.ordinal == 0;
        assert Rounding.TiesToEven.toString() == "TiesToEven";

        assert Rounding.TiesToEven.estimateStringLength() == 10;

        StringBuffer buf = new StringBuffer();
        String       s   = Rounding.TiesToEven.appendTo(buf).toString();
        assert s == "TiesToEven";
    }

    void testTiesToAway() {
        assert Rounding.TiesToAway.name == "TiesToAway";
        assert Rounding.TiesToAway.ordinal == 1;
        assert Rounding.TiesToAway.toString() == "TiesToAway";

        assert Rounding.TiesToAway.estimateStringLength() == 10;

        StringBuffer buf = new StringBuffer();
        String       s   = Rounding.TiesToAway.appendTo(buf).toString();
        assert s == "TiesToAway";
    }

    void testTowardPositive() {
        assert Rounding.TowardPositive.name == "TowardPositive";
        assert Rounding.TowardPositive.ordinal == 2;
        assert Rounding.TowardPositive.toString() == "TowardPositive";

        assert Rounding.TowardPositive.estimateStringLength() == 14;

        StringBuffer buf = new StringBuffer();
        String       s   = Rounding.TowardPositive.appendTo(buf).toString();
        assert s == "TowardPositive";
    }

    void testTowardZero() {
        assert Rounding.TowardZero.name == "TowardZero";
        assert Rounding.TowardZero.ordinal == 3;
        assert Rounding.TowardZero.toString() == "TowardZero";

        assert Rounding.TowardZero.estimateStringLength() == 10;

        StringBuffer buf = new StringBuffer();
        String       s   = Rounding.TowardZero.appendTo(buf).toString();
        assert s == "TowardZero";
    }

    void testTowardNegative() {
        assert Rounding.TowardNegative.name == "TowardNegative";
        assert Rounding.TowardNegative.ordinal == 4;
        assert Rounding.TowardNegative.toString() == "TowardNegative";

        assert Rounding.TowardNegative.estimateStringLength() == 14;

        StringBuffer buf = new StringBuffer();
        String       s   = Rounding.TowardNegative.appendTo(buf).toString();
        assert s == "TowardNegative";
    }
}
