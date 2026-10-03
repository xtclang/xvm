import ecstasy.collections.Array.Mutability;

/**
 * Tests for the Array.Mutability enum.
 */
class MutabilityTests {

    void run() {
        testMutabilityCount();
        testMutabilityNames();
        testMutabilityValues();

        testNext();
        testPrev();
        testSkip();
        testStepsTo();

        testOrder();

        testConstant();
        testPersistent();
        testFixed();
        testMutable();
    }

    void testMutabilityCount() {
        assert Mutability.count == 4;
    }

    void testMutabilityNames() {
        assert Mutability.names.size == 4;
        assert Mutability.names[0] == "Constant";
        assert Mutability.names[1] == "Persistent";
        assert Mutability.names[2] == "Fixed";
        assert Mutability.names[3] == "Mutable";
    }

    void testMutabilityValues() {
        assert Mutability.values.size == 4;
        assert Mutability.values[0] == Constant;
        assert Mutability.values[1] == Persistent;
        assert Mutability.values[2] == Fixed;
        assert Mutability.values[3] == Mutable;
    }

    void testNext() {
        assert Mutability next := Mutability.Constant.next();
        assert next == Persistent;
        assert next := Mutability.Persistent.next();
        assert next == Fixed;
        assert next := Mutability.Fixed.next();
        assert next == Mutable;
        Boolean hasNext = Mutability.Mutable.next();
        assert hasNext == False;

        assert Sequential s := nextAsSequential(Mutability.Constant);
        assert s == Mutability.Persistent;
        assert s := nextAsSequential(Mutability.Persistent);
        assert s == Mutability.Fixed;
        assert s := nextAsSequential(Mutability.Fixed);
        assert s == Mutability.Mutable;
        hasNext = nextAsSequential(Mutability.Mutable);
        assert hasNext == False;

// TODO see nextAsEnum below
//        assert Enum e := nextAsEnum(Mutability.Constant);
//        assert e == Mutability.Persistent;
//        assert e := nextAsEnum(Mutability.Persistent);
//        assert e == Mutability.Fixed;
//        assert e := nextAsEnum(Mutability.Fixed);
//        assert e == Mutability.Mutable;
//        hasNext = nextAsEnum(Mutability.Mutable);
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
        Boolean hasPrev = Mutability.Constant.prev();
        assert hasPrev == False;
        assert Mutability prev := Mutability.Persistent.prev();
        assert prev == Constant;
        assert prev := Mutability.Fixed.prev();
        assert prev == Persistent;
        assert prev := Mutability.Mutable.prev();
        assert prev == Fixed;

        hasPrev = prevAsSequential(Mutability.Constant);
        assert hasPrev == False;
        assert Sequential s := prevAsSequential(Mutability.Persistent);
        assert s == Mutability.Constant;
        assert s := prevAsSequential(Mutability.Fixed);
        assert s == Mutability.Persistent;
        assert s := prevAsSequential(Mutability.Mutable);
        assert s == Mutability.Fixed;

// TODO see prevAsEnum below
//        hasPrev = prevAsEnum(Mutability.Constant);
//        assert hasPrev == False;
//        assert Enum e := prevAsEnum(Mutability.Persistent);
//        assert e == Mutability.Constant;
//        assert e := prevAsEnum(Mutability.Fixed);
//        assert e == Mutability.Persistent;
//        assert e := prevAsEnum(Mutability.Mutable);
//        assert e == Mutability.Fixed;
    }

    conditional Sequential prevAsSequential(Sequential s) {
        return s.prev();
    }

// TODO requires calls to methods on nEnum to be invokevirtual instead of invokeinterface
//    conditional Enum prevAsEnum(Enum e) {
//        return e.next();
//    }

    void testSkip() {
        Mutability b = Mutability.Constant.skip(0);
        assert b == Mutability.Constant;
        b = Mutability.Constant.skip(1);
        assert b == Persistent;
        b = Mutability.Constant.skip(2);
        assert b == Fixed;

        b = Mutability.Persistent.skip(0);
        assert b == Mutability.Persistent;
        b = Mutability.Persistent.skip(-1);
        assert b == Mutability.Constant;
        b = Mutability.Persistent.skip(1);
        assert b == Mutability.Fixed;

        b = Mutability.Fixed.skip(0);
        assert b == Mutability.Fixed;
        b = Mutability.Fixed.skip(-1);
        assert b == Mutability.Persistent;
        b = Mutability.Fixed.skip(-2);
        assert b == Mutability.Constant;

        try {
            Mutability.Constant.skip(4);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Mutability.Constant.skip(-1);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Mutability.Persistent.skip(3);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Mutability.Persistent.skip(-2);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Mutability.Fixed.skip(2);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Mutability.Fixed.skip(-4);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        Sequential s = skipAsSequential(Mutability.Persistent, 1);
        assert s == Mutability.Fixed;

        try {
            skipAsSequential(Mutability.Fixed, -3);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

// TODO see skipAsEnum below
//        Enum e = skipAsEnum(Mutability.Constant, 2);
//        assert e == Mutability.Fixed;
//
//        try {
//            skipAsEnum(Mutability.Persistent, -3);
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
        Mutability c = Constant;
        Mutability p = Persistent;
        Mutability f = Fixed;
        Mutability m = Mutable;

        assert Mutability.Constant.stepsTo(Constant) == 0;
        assert c.stepsTo(Persistent) == 1;
        assert c.stepsTo(Fixed) == 2;
        assert c.stepsTo(Mutable) == 3;

        assert Mutability.Persistent.stepsTo(Persistent) == 0;
        assert p.stepsTo(Constant) == -1;
        assert p.stepsTo(Fixed) == 1;
        assert p.stepsTo(Mutable) == 2;

        assert Mutability.Fixed.stepsTo(Fixed) == 0;
        assert f.stepsTo(Constant) == -2;
        assert f.stepsTo(Persistent) == -1;
        assert f.stepsTo(Mutable) == 1;

        assert Mutability.Mutable.stepsTo(Mutable) == 0;
        assert m.stepsTo(Constant) == -3;
        assert m.stepsTo(Persistent) == -2;
        assert m.stepsTo(Fixed) == -1;

        assert stepsToAsSequential(Mutability.Constant, Mutability.Fixed) == 2;
        assert stepsToAsSequential(Mutability.Fixed, Mutability.Persistent) == -1;

// TODO see stepsToAsEnum below
//        assert stepsToAsEnum(Mutability.Constant, Mutability.Fixed) == 2;
//        assert stepsToAsEnum(Mutability.Fixed, Mutability.Persistent) == -1;
    }

    Int stepsToAsSequential(Sequential s1, Sequential s2) {
        return s1.stepsTo(s2);
    }

// TODO requires calls to methods on nEnum to be invokevirtual instead of invokeinterface
//    Int stepsToAsEnum(Enum e1, Enum e2) {
//        return e1.stepsTo(e2);
//    }

    void testOrder() {
        assert Mutability.Constant < Mutability.Persistent;
        assert Mutability.Constant < Mutability.Fixed;
        assert Mutability.Persistent > Mutability.Constant;
        assert Mutability.Persistent < Mutability.Fixed;
        assert Mutability.Fixed > Mutability.Constant;
        assert Mutability.Fixed > Mutability.Persistent;
        assert Mutability.Mutable > Mutability.Constant;
        assert Mutability.Mutable > Mutability.Persistent;

// TODO see TODO in OpTest.buildBinary()
//        assert Mutability.Constant <=> Mutability.Constant == Equal;
//        assert Mutability.Constant <=> Mutability.Persistent == Lesser;
//        assert Mutability.Constant <=> Mutability.Fixed == Lesser;
//        assert Mutability.Constant <=> Mutability.Mutable == Lesser;
//        assert Mutability.Persistent <=> Mutability.Constant == Greater;
//        assert Mutability.Persistent <=> Mutability.Persistent == Equal;
//        assert Mutability.Persistent <=> Mutability.Fixed == Lesser;
//        assert Mutability.Persistent <=> Mutability.Mutable == Lesser;
//        assert Mutability.Fixed <=> Mutability.Constant == Greater;
//        assert Mutability.Fixed <=> Mutability.Persistent == Greater;
//        assert Mutability.Fixed <=> Mutability.Fixed == Equal;
//        assert Mutability.Fixed <=> Mutability.Mutable == Lesser;
//        assert Mutability.Mutable > Mutability.Fixed;
//        assert Mutability.Mutable <=> Mutability.Constant == Greater;
//        assert Mutability.Mutable <=> Mutability.Persistent == Greater;
//        assert Mutability.Mutable <=> Mutability.Fixed == Greater;
//        assert Mutability.Mutable <=> Mutability.Mutable == Equal;
    }

    void testConstant() {
        assert Mutability.Constant.name == "Constant";
        assert Mutability.Constant.ordinal == 0;
        assert Mutability.Constant.toString() == "Constant";

        assert Mutability.Constant.estimateStringLength() == 8;

        StringBuffer buf = new StringBuffer();
        String       s   = Mutability.Constant.appendTo(buf).toString();
        assert s == "Constant";
    }

    void testPersistent() {
        assert Mutability.Persistent.name == "Persistent";
        assert Mutability.Persistent.ordinal == 1;
        assert Mutability.Persistent.toString() == "Persistent";

        assert Mutability.Persistent.estimateStringLength() == 10;

        StringBuffer buf = new StringBuffer();
        String       s   = Mutability.Persistent.appendTo(buf).toString();
        assert s == "Persistent";
    }

    void testFixed() {
        assert Mutability.Fixed.name == "Fixed";
        assert Mutability.Fixed.ordinal == 2;
        assert Mutability.Fixed.toString() == "Fixed";

        assert Mutability.Fixed.estimateStringLength() == 5;

        StringBuffer buf = new StringBuffer();
        String       s   = Mutability.Fixed.appendTo(buf).toString();
        assert s == "Fixed";
    }

    void testMutable() {
        assert Mutability.Mutable.name == "Mutable";
        assert Mutability.Mutable.ordinal == 3;
        assert Mutability.Mutable.toString() == "Mutable";

        assert Mutability.Mutable.estimateStringLength() == 7;

        StringBuffer buf = new StringBuffer();
        String       s   = Mutability.Mutable.appendTo(buf).toString();
        assert s == "Mutable";
    }
}
