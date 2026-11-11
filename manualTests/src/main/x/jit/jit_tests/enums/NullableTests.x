/**
 * Tests for the a Nullable enum.
 */
class NullableTests {

    void run() {
        testNullableCount();
        testNullableNames();
        testNullableValues();

        testNext();
        testPrev();
        testSkip();
        testStepsTo();

        testNull();
    }

    void testNullableCount() {
        assert Nullable.count == 1;
    }

    void testNullableNames() {
        assert Nullable.names.size == 1;
        assert Nullable.names[0] == "Null";
    }

    void testNullableValues() {
        assert Nullable.values.size == 1;
        assert Nullable.values[0] == Null;
    }

    void testNext() {
        Boolean hasNext = Nullable.Null.next();
        assert hasNext == False;
        hasNext = nextAsSequential(Nullable.Null);
        assert hasNext == False;

        hasNext = nextAsEnum(Nullable.Null);
        assert hasNext == False;
    }

    conditional Sequential nextAsSequential(Sequential s) {
        return s.next();
    }

    conditional Enum nextAsEnum(Enum e) {
        return e.next();
    }

    void testPrev() {
        Boolean hasPrev = Nullable.Null.prev();
        assert hasPrev == False;

        hasPrev = prevAsSequential(Nullable.Null);
        assert hasPrev == False;

        hasPrev = prevAsEnum(Nullable.Null);
        assert hasPrev == False;
    }

    conditional Sequential prevAsSequential(Sequential s) {
        return s.prev();
    }

    conditional Enum prevAsEnum(Enum e) {
        return e.next();
    }

    void testSkip() {
        Nullable b = Nullable.Null.skip(0);
        assert b.ordinal == 0;

        try {
            Nullable.Null.skip(1);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            Nullable.Null.skip(-1);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        Sequential s = skipAsSequential(Nullable.Null, 0);
        assert s == Nullable.Null;

        try {
            skipAsSequential(Nullable.Null, -3);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        Enum e = skipAsEnum(Nullable.Null, 0);
        assert e == Nullable.Null;

        try {
            skipAsEnum(Nullable.Null, -3);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }
    }

    Sequential skipAsSequential(Sequential s, Int steps) {
        return s.skip(steps);
    }

    Enum skipAsEnum(Enum e, Int steps) {
        return e.skip(steps);
    }

    void testStepsTo() {
        assert Nullable.Null.stepsTo(Null) == 0;
        assert stepsToAsSequential(Nullable.Null, Nullable.Null) == 0;
        assert stepsToAsSequential(Nullable.Null, Nullable.Null) == 0;

        assert stepsToAsEnum(Nullable.Null, Nullable.Null) == 0;
    }

    Int stepsToAsSequential(Sequential s1, Sequential s2) {
        return s1.stepsTo(s2);
    }

    Int stepsToAsEnum(Enum e1, Enum e2) {
        return e1.stepsTo(e2);
    }

    void testNull() {
        assert Nullable.Null.name == "Null";
        assert Nullable.Null.ordinal == 0;
        assert Nullable.Null.toString() == "Null";

        assert Nullable.Null.estimateStringLength() == 4;

        StringBuffer buf = new StringBuffer();
        String       s   = Nullable.Null.appendTo(buf).toString();
        assert s == "Null";
    }
}
