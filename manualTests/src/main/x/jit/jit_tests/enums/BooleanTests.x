/**
 * Tests for the Boolean enum.
 */
class BooleanTests {

    void run() {
        testBooleanCount();
        testBooleanNames();
        testBooleanValues();

        testNext();
        testPrev();
        testStepsTo();
        testSkip();

        testOrder();

        testFalse();
        testTrue();

        testAnd();
        testOr();
        testXor();
        testNot();

        testToBit();
        testToByte();
        testToInt8();
        testToInt16();
        testToInt32();
        testToInt64();
        testToInt128();
        testToIntN();
        testToUInt8();
        testToUInt16();
        testToUInt32();
        testToUInt64();
        testToUInt128();
        testToUIntN();
    }

    void testBooleanCount() {
        assert Boolean.count == 2;
    }

    void testBooleanNames() {
        assert Boolean.names.size == 2;
        assert Boolean.names[0] == "False";
        assert Boolean.names[1] == "True";
    }

    void testBooleanValues() {
        assert Boolean.values.size == 2;
        assert Boolean.values[0] == False;
        assert Boolean.values[1] == True;
    }

    void testNext() {
        assert Boolean next := False.next();
        assert next == True;
        Boolean hasNext = True.next();
        assert hasNext == False;

        Sequential s = nextAsSequential(False);
        assert s == True;

// TODO see nextAsEnum below
//        Enum e = nextAsEnum(False);
//        assert e == True;
    }

    conditional Sequential nextAsSequential(Sequential s) {
        return s.next();
    }

//    conditional Enum nextAsEnum(Enum e) {
// TODO requires calls to methods on nEnum to be invokevirtual instead of invokeinterface
//        return e.next();
//    }

    void testPrev() {
        Boolean hasPrev = False.prev();
        assert hasPrev == False;
        assert Boolean prev := True.prev();
        assert prev == False;
    }

    void testSkip() {
        Boolean b = False.skip(0);
        assert b == False;
        b = False.skip(1);
        assert b == True;

        b = True.skip(0);
        assert b == True;
        b = True.skip(-1);
        assert b == False;

        try {
            False.skip(2);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            False.skip(-1);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            True.skip(1);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }

        try {
            True.skip(-2);
            assert as "should have thrown OOB";
        } catch (OutOfBounds _) {
            // expected
        }
    }

    void testStepsTo() {
        assert False.stepsTo(False) == 0;
// TODO why is this a compilation error, but the line above is not?
//        assert False.stepsTo(True) == 1;
//        assert True.stepsTo(False) == -1;
        Boolean t = True;
        Boolean f = False;
        assert f.stepsTo(t) == 1;
        assert t.stepsTo(f) == -1;
        assert True.stepsTo(True) == 0;
    }

    void testOrder() {
        assert False < True;
        assert True > False;
// TODO see TODO in OpTest.buildBinary()
//        assert False <=> False == Equal;
//        assert False <=> True == Lesser;
//        assert True <=> False == Greater;
//        assert True <=> True == Equal;
    }

    void testFalse() {
        assert False.name == "False";
        assert False.ordinal == 0;
        assert False.toString() == "False";

        assert False.estimateStringLength() == 5;

        StringBuffer buf = new StringBuffer();
        String       s   = False.appendTo(buf).toString();
        assert s == "False";
    }

    void testTrue() {
        assert True.name == "True";
        assert True.ordinal == 1;
        assert True.toString() == "True";

        assert True.estimateStringLength() == 4;

        StringBuffer buf = new StringBuffer();
        String       s   = True.appendTo(buf).toString();
        assert s == "True";
    }

    void testAnd() {
        assert True & True == True;
        assert False & True == False;
        assert True & False == False;
        assert False & False == False;
    }

    void testOr() {
        assert True | True == True;
        assert False | True == True;
        assert True | False == True;
        assert False | False == False;
    }

    void testXor() {
        assert True ^ True == False;
        assert False ^ True == True;
        assert True ^ False == True;
        assert False ^ False == False;
    }

    void testNot() {
        assert !True == False;
        assert !False == True;
    }

    void testToBit() {
        assert False.toBit() == Bit:0;
        assert True.toBit() == Bit:1;
    }

    void testToByte() {
        assert False.toByte() == Byte:0;
        assert True.toByte() == Byte:1;
    }

    void testToInt8() {
        assert False.toInt8() == Int8:0;
        assert True.toInt8() == Int8:1;
    }

    void testToInt16() {
        assert False.toInt16() == Int16:0;
        assert True.toInt16() == Int16:1;
    }

    void testToInt32() {
        assert False.toInt32() == Int32:0;
        assert True.toInt32() == Int32:1;
    }

    void testToInt64() {
        assert False.toInt64() == Int64:0;
        assert True.toInt64() == Int64:1;
    }

    void testToInt128() {
        assert False.toInt128() == Int128:0;
        assert True.toInt128() == Int128:1;
    }

    void testToIntN() {
        IntN zero = 0;
        IntN one  = 1;
        assert False.toIntN() == zero;
        assert True.toIntN() == one;
    }

    void testToUInt8() {
        assert False.toUInt8() == UInt8:0;
        assert True.toUInt8() == UInt8:1;
    }

    void testToUInt16() {
        assert False.toUInt16() == UInt16:0;
        assert True.toUInt16() == UInt16:1;
    }

    void testToUInt32() {
        assert False.toUInt32() == UInt32:0;
        assert True.toUInt32() == UInt32:1;
    }

    void testToUInt64() {
        assert False.toUInt64() == UInt64:0;
        assert True.toUInt64() == UInt64:1;
    }

    void testToUInt128() {
        assert False.toUInt128() == UInt128:0;
        assert True.toUInt128() == UInt128:1;
    }

    void testToUIntN() {
        UIntN zero = 0;
        UIntN one  = 1;
        assert False.toUIntN() == zero;
        assert True.toUIntN() == one;
    }
}
