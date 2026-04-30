package constTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Const Tests >>>>");

        emptyConstShouldBeEqual();
        emptyConstShouldBeOrderable();
        emptyConstShouldHaveNoneZeroHashCode();

        simpleConstEquality();
        simpleConstHashCode();
        simpleConstCompare();

        constWithConstEquality();
        constWithConstHashCode();
        constWithConstCompare();

        constWithBitEquality();
        constWithBitCompare();
        constWithBitHashCode();

        constWithBooleanEquality();
        constWithBooleanCompare();
        constWithBooleanHashCode();

        constWithCharEquality();
        constWithCharCompare();
        constWithCharHashCode();

        constWithDec32Equality();
        constWithDec32Compare();
        constWithDec32HashCode();

        constWithDec64Equality();
        constWithDec64Compare();
        constWithDec64HashCode();

        constWithFloat32Equality();
        constWithFloat32Compare();
        constWithFloat32HashCode();

        constWithFloat64Equality();
        constWithFloat64Compare();
        constWithFloat64HashCode();

        constWithInt8Equality();
        constWithInt8Compare();
        constWithInt8HashCode();

        constWithInt16Equality();
        constWithInt16Compare();
        constWithInt16HashCode();

        constWithInt32Equality();
        constWithInt32Compare();
        constWithInt32HashCode();

        constWithInt64Equality();
        constWithInt64Compare();
        constWithInt64HashCode();

        constWithInt128Equality();
        constWithInt128Compare();
        constWithInt128HashCode();

        constWithNibbleEquality();
        constWithNibbleCompare();
        constWithNibbleHashCode();

        constWithStringEquality();
        constWithStringCompare();
        constWithStringHashCode();

        constWithUInt8Equality();
        constWithUInt8Compare();
        constWithUInt8HashCode();

        constWithUInt16Equality();
        constWithUInt16Compare();
        constWithUInt16HashCode();

        constWithUInt32Equality();
        constWithUInt32Compare();
        constWithUInt32HashCode();

        constWithUInt64Equality();
        constWithUInt64Compare();
        constWithUInt64HashCode();

        constWithUInt128Equality();
        constWithUInt128Compare();
        constWithUInt128HashCode();

        testClassHierarchyEqualsConstWithNoOwnProps();
        testClassHierarchyEqualsConstWithOwnProps();
        testClassHierarchyCompareConstWithNoOwnProps();
        testClassHierarchyCompareConstWithOwnProps();
        testClassHierarchyHashCodeConstWithNoOwnProps();
        testClassHierarchyHashCodeConstWithOwnProps();

        testConstHierarchyEqualsConstWithNoOwnProps();
        testConstHierarchyEqualsConstWithOwnProps();
        testConstHierarchyCompareConstWithNoOwnProps();
        testConstHierarchyCompareConstWithOwnProps();
        testConstHierarchyHashCodeConstWithNoOwnProps();
        testConstHierarchyHashCodeConstWithOwnProps();

        testConstWithNotComparableProp();
        testConstWithNotOrderableProp();
        testConstWithNotHashableProp();

        console.print("<<<< Finished Const Tests <<<<<");
    }

    void emptyConstShouldBeEqual() {
        EmptyConst c1 = new EmptyConst();
        EmptyConst c2 = new EmptyConst();
        assert c1 == c2;
    }

    void emptyConstShouldBeOrderable() {
        EmptyConst c1 = new EmptyConst();
        EmptyConst c2 = new EmptyConst();
        assert c1 <=> c2 == Equal;
    }

    void emptyConstShouldHaveNoneZeroHashCode() {
        EmptyConst c1 = new EmptyConst();
        EmptyConst c2 = new EmptyConst();
        assert c1.hashCode() != 0;
        assert c2.hashCode() != 0;
        assert c1.hashCode() == c2.hashCode();
    }

    void simpleConstEquality() {
        SimpleConst c1 = new SimpleConst(1, "a");
        SimpleConst c2 = new SimpleConst(1, "a");
        SimpleConst c3 = new SimpleConst(2, "a");
        SimpleConst c4 = new SimpleConst(1, "b");
        assert c1 == c2;
        assert c1 != c3;
        assert c1 != c4;
    }

    void simpleConstHashCode() {
        SimpleConst c1 = new SimpleConst(1, "a");
        SimpleConst c2 = new SimpleConst(1, "a");
        SimpleConst c3 = new SimpleConst(2, "a");
        SimpleConst c4 = new SimpleConst(1, "b");
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
        assert c1.hashCode() != c4.hashCode();
    }

    void simpleConstCompare() {
        SimpleConst c1 = new SimpleConst(1, "a");
        SimpleConst c2 = new SimpleConst(1, "a");
        SimpleConst c3 = new SimpleConst(2, "a");
        SimpleConst c4 = new SimpleConst(1, "b");
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c1 <=> c4 == Lesser;
        assert c3 <=> c2 == Greater;
        assert c4 <=> c1 == Greater;
    }

    void constWithConstEquality() {
        ConstWithConst c1 = new ConstWithConst(new SimpleConst(1, "a"));
        ConstWithConst c2 = new ConstWithConst(new SimpleConst(1, "a"));
        ConstWithConst c3 = new ConstWithConst(new SimpleConst(2, "a"));
        ConstWithConst c4 = new ConstWithConst(new SimpleConst(1, "b"));
        assert c1 == c2;
        assert c1 != c3;
        assert c1 != c4;
    }

    void constWithConstHashCode() {
        ConstWithConst c1 = new ConstWithConst(new SimpleConst(1, "a"));
        ConstWithConst c2 = new ConstWithConst(new SimpleConst(1, "a"));
        ConstWithConst c3 = new ConstWithConst(new SimpleConst(2, "a"));
        ConstWithConst c4 = new ConstWithConst(new SimpleConst(1, "b"));
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
        assert c1.hashCode() != c4.hashCode();
    }

    void constWithConstCompare() {
        ConstWithConst c1 = new ConstWithConst(new SimpleConst(1, "a"));
        ConstWithConst c2 = new ConstWithConst(new SimpleConst(1, "a"));
        ConstWithConst c3 = new ConstWithConst(new SimpleConst(2, "a"));
        ConstWithConst c4 = new ConstWithConst(new SimpleConst(1, "b"));
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c1 <=> c4 == Lesser;
        assert c3 <=> c2 == Greater;
        assert c4 <=> c1 == Greater;
    }

    void constWithBitEquality() {
        ConstWithBit c1 = new ConstWithBit(0);
        ConstWithBit c2 = new ConstWithBit(0);
        ConstWithBit c3 = new ConstWithBit(1);
        assert c1 == c2;
        assert c1 != c3;
    }

    void constWithBitCompare() {
        ConstWithBit c1 = new ConstWithBit(0);
        ConstWithBit c2 = new ConstWithBit(0);
        ConstWithBit c3 = new ConstWithBit(1);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void constWithBitHashCode() {
        ConstWithBit c1 = new ConstWithBit(0);
        ConstWithBit c2 = new ConstWithBit(0);
        ConstWithBit c3 = new ConstWithBit(1);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void constWithBooleanEquality() {
        ConstWithBoolean c1 = new ConstWithBoolean(False);
        ConstWithBoolean c2 = new ConstWithBoolean(False);
        ConstWithBoolean c3 = new ConstWithBoolean(True);
        assert c1 == c2;
        assert c1 != c3;
    }

    void constWithBooleanCompare() {
        ConstWithBoolean c1 = new ConstWithBoolean(False);
        ConstWithBoolean c2 = new ConstWithBoolean(False);
        ConstWithBoolean c3 = new ConstWithBoolean(True);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void constWithBooleanHashCode() {
        ConstWithBoolean c1 = new ConstWithBoolean(False);
        ConstWithBoolean c2 = new ConstWithBoolean(False);
        ConstWithBoolean c3 = new ConstWithBoolean(True);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void constWithCharEquality() {
        ConstWithChar c1 = new ConstWithChar('a');
        ConstWithChar c2 = new ConstWithChar('a');
        ConstWithChar c3 = new ConstWithChar('b');
        assert c1 == c2;
        assert c1 != c3;
    }

    void constWithCharCompare() {
        ConstWithChar c1 = new ConstWithChar('a');
        ConstWithChar c2 = new ConstWithChar('a');
        ConstWithChar c3 = new ConstWithChar('b');
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void constWithCharHashCode() {
        ConstWithChar c1 = new ConstWithChar('a');
        ConstWithChar c2 = new ConstWithChar('a');
        ConstWithChar c3 = new ConstWithChar('b');
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void constWithDec32Equality() {
        ConstWithDec32 c1 = new ConstWithDec32(1.0);
        ConstWithDec32 c2 = new ConstWithDec32(1.0);
        ConstWithDec32 c3 = new ConstWithDec32(2.0);
        assert c1 == c2;
        assert c1 != c3;
    }

    void constWithDec32Compare() {
        ConstWithDec32 c1 = new ConstWithDec32(1.0);
        ConstWithDec32 c2 = new ConstWithDec32(1.0);
        ConstWithDec32 c3 = new ConstWithDec32(2.0);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void constWithDec32HashCode() {
        ConstWithDec32 c1 = new ConstWithDec32(1.0);
        ConstWithDec32 c2 = new ConstWithDec32(1.0);
        ConstWithDec32 c3 = new ConstWithDec32(2.0);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void constWithDec64Equality() {
        ConstWithDec64 c1 = new ConstWithDec64(1.0);
        ConstWithDec64 c2 = new ConstWithDec64(1.0);
        ConstWithDec64 c3 = new ConstWithDec64(2.0);
        assert c1 == c2;
        assert c1 != c3;
    }

    void constWithDec64Compare() {
        ConstWithDec64 c1 = new ConstWithDec64(1.0);
        ConstWithDec64 c2 = new ConstWithDec64(1.0);
        ConstWithDec64 c3 = new ConstWithDec64(2.0);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void constWithDec64HashCode() {
        ConstWithDec64 c1 = new ConstWithDec64(1.0);
        ConstWithDec64 c2 = new ConstWithDec64(1.0);
        ConstWithDec64 c3 = new ConstWithDec64(2.0);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void constWithFloat32Equality() {
        ConstWithFloat32 c1 = new ConstWithFloat32(1.0);
        ConstWithFloat32 c2 = new ConstWithFloat32(1.0);
        ConstWithFloat32 c3 = new ConstWithFloat32(2.0);
        assert c1 == c2;
        assert c1 != c3;
    }

    void constWithFloat32Compare() {
        ConstWithFloat32 c1 = new ConstWithFloat32(1.0);
        ConstWithFloat32 c2 = new ConstWithFloat32(1.0);
        ConstWithFloat32 c3 = new ConstWithFloat32(2.0);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void constWithFloat32HashCode() {
        ConstWithFloat32 c1 = new ConstWithFloat32(1.0);
        ConstWithFloat32 c2 = new ConstWithFloat32(1.0);
        ConstWithFloat32 c3 = new ConstWithFloat32(2.0);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void constWithFloat64Equality() {
        ConstWithFloat64 c1 = new ConstWithFloat64(1.0);
        ConstWithFloat64 c2 = new ConstWithFloat64(1.0);
        ConstWithFloat64 c3 = new ConstWithFloat64(2.0);
        assert c1 == c2;
        assert c1 != c3;
    }

    void constWithFloat64Compare() {
        ConstWithFloat64 c1 = new ConstWithFloat64(1.0);
        ConstWithFloat64 c2 = new ConstWithFloat64(1.0);
        ConstWithFloat64 c3 = new ConstWithFloat64(2.0);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void constWithFloat64HashCode() {
        ConstWithFloat64 c1 = new ConstWithFloat64(1.0);
        ConstWithFloat64 c2 = new ConstWithFloat64(1.0);
        ConstWithFloat64 c3 = new ConstWithFloat64(2.0);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void constWithInt8Equality() {
        ConstWithInt8 c1 = new ConstWithInt8(1);
        ConstWithInt8 c2 = new ConstWithInt8(1);
        ConstWithInt8 c3 = new ConstWithInt8(2);
        assert c1 == c2;
        assert c1 != c3;
    }

    void constWithInt8Compare() {
        ConstWithInt8 c1 = new ConstWithInt8(1);
        ConstWithInt8 c2 = new ConstWithInt8(1);
        ConstWithInt8 c3 = new ConstWithInt8(2);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void constWithInt8HashCode() {
        ConstWithInt8 c1 = new ConstWithInt8(1);
        ConstWithInt8 c2 = new ConstWithInt8(1);
        ConstWithInt8 c3 = new ConstWithInt8(2);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void constWithInt16Equality() {
        ConstWithInt16 c1 = new ConstWithInt16(1);
        ConstWithInt16 c2 = new ConstWithInt16(1);
        ConstWithInt16 c3 = new ConstWithInt16(2);
        assert c1 == c2;
        assert c1 != c3;
    }

    void constWithInt16Compare() {
        ConstWithInt16 c1 = new ConstWithInt16(1);
        ConstWithInt16 c2 = new ConstWithInt16(1);
        ConstWithInt16 c3 = new ConstWithInt16(2);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void constWithInt16HashCode() {
        ConstWithInt16 c1 = new ConstWithInt16(1);
        ConstWithInt16 c2 = new ConstWithInt16(1);
        ConstWithInt16 c3 = new ConstWithInt16(2);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void constWithInt32Equality() {
        ConstWithInt32 c1 = new ConstWithInt32(1);
        ConstWithInt32 c2 = new ConstWithInt32(1);
        ConstWithInt32 c3 = new ConstWithInt32(2);
        assert c1 == c2;
        assert c1 != c3;
    }

    void constWithInt32Compare() {
        ConstWithInt32 c1 = new ConstWithInt32(1);
        ConstWithInt32 c2 = new ConstWithInt32(1);
        ConstWithInt32 c3 = new ConstWithInt32(2);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void constWithInt32HashCode() {
        ConstWithInt32 c1 = new ConstWithInt32(1);
        ConstWithInt32 c2 = new ConstWithInt32(1);
        ConstWithInt32 c3 = new ConstWithInt32(2);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void constWithInt64Equality() {
        ConstWithInt64 c1 = new ConstWithInt64(1);
        ConstWithInt64 c2 = new ConstWithInt64(1);
        ConstWithInt64 c3 = new ConstWithInt64(2);
        assert c1 == c2;
        assert c1 != c3;
    }

    void constWithInt64Compare() {
        ConstWithInt64 c1 = new ConstWithInt64(1);
        ConstWithInt64 c2 = new ConstWithInt64(1);
        ConstWithInt64 c3 = new ConstWithInt64(2);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void constWithInt64HashCode() {
        ConstWithInt64 c1 = new ConstWithInt64(1);
        ConstWithInt64 c2 = new ConstWithInt64(1);
        ConstWithInt64 c3 = new ConstWithInt64(2);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void constWithInt128Equality() {
        ConstWithInt128 c1 = new ConstWithInt128(1);
        ConstWithInt128 c2 = new ConstWithInt128(1);
        ConstWithInt128 c3 = new ConstWithInt128(2);
        assert c1 == c2;
        assert c1 != c3;
    }

    void constWithInt128Compare() {
        ConstWithInt128 c1 = new ConstWithInt128(1);
        ConstWithInt128 c2 = new ConstWithInt128(1);
        ConstWithInt128 c3 = new ConstWithInt128(2);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void constWithInt128HashCode() {
        ConstWithInt128 c1 = new ConstWithInt128(1);
        ConstWithInt128 c2 = new ConstWithInt128(1);
        ConstWithInt128 c3 = new ConstWithInt128(2);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void constWithNibbleEquality() {
        ConstWithNibble c1 = new ConstWithNibble(0);
        ConstWithNibble c2 = new ConstWithNibble(0);
        ConstWithNibble c3 = new ConstWithNibble(1);
        assert c1 == c2;
        assert c1 != c3;
    }

    void constWithNibbleCompare() {
        ConstWithNibble c1 = new ConstWithNibble(0);
        ConstWithNibble c2 = new ConstWithNibble(0);
        ConstWithNibble c3 = new ConstWithNibble(1);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void constWithNibbleHashCode() {
        ConstWithNibble c1 = new ConstWithNibble(0);
        ConstWithNibble c2 = new ConstWithNibble(0);
        ConstWithNibble c3 = new ConstWithNibble(1);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void constWithStringEquality() {
        ConstWithString c1 = new ConstWithString("a");
        ConstWithString c2 = new ConstWithString("a");
        ConstWithString c3 = new ConstWithString("b");
        assert c1 == c2;
        assert c1 != c3;
    }

    void constWithStringCompare() {
        ConstWithString c1 = new ConstWithString("a");
        ConstWithString c2 = new ConstWithString("a");
        ConstWithString c3 = new ConstWithString("b");
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void constWithStringHashCode() {
        ConstWithString c1 = new ConstWithString("a");
        ConstWithString c2 = new ConstWithString("a");
        ConstWithString c3 = new ConstWithString("b");
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void constWithUInt8Equality() {
        ConstWithUInt8 c1 = new ConstWithUInt8(1);
        ConstWithUInt8 c2 = new ConstWithUInt8(1);
        ConstWithUInt8 c3 = new ConstWithUInt8(2);
        assert c1 == c2;
        assert c1 != c3;
    }

    void constWithUInt8Compare() {
        ConstWithUInt8 c1 = new ConstWithUInt8(1);
        ConstWithUInt8 c2 = new ConstWithUInt8(1);
        ConstWithUInt8 c3 = new ConstWithUInt8(2);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void constWithUInt8HashCode() {
        ConstWithUInt8 c1 = new ConstWithUInt8(1);
        ConstWithUInt8 c2 = new ConstWithUInt8(1);
        ConstWithUInt8 c3 = new ConstWithUInt8(2);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void constWithUInt16Equality() {
        ConstWithUInt16 c1 = new ConstWithUInt16(1);
        ConstWithUInt16 c2 = new ConstWithUInt16(1);
        ConstWithUInt16 c3 = new ConstWithUInt16(2);
        assert c1 == c2;
        assert c1 != c3;
    }

    void constWithUInt16Compare() {
        ConstWithUInt16 c1 = new ConstWithUInt16(1);
        ConstWithUInt16 c2 = new ConstWithUInt16(1);
        ConstWithUInt16 c3 = new ConstWithUInt16(2);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void constWithUInt16HashCode() {
        ConstWithUInt16 c1 = new ConstWithUInt16(1);
        ConstWithUInt16 c2 = new ConstWithUInt16(1);
        ConstWithUInt16 c3 = new ConstWithUInt16(2);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void constWithUInt32Equality() {
        ConstWithUInt32 c1 = new ConstWithUInt32(1);
        ConstWithUInt32 c2 = new ConstWithUInt32(1);
        ConstWithUInt32 c3 = new ConstWithUInt32(2);
        assert c1 == c2;
        assert c1 != c3;
    }

    void constWithUInt32Compare() {
        ConstWithUInt32 c1 = new ConstWithUInt32(1);
        ConstWithUInt32 c2 = new ConstWithUInt32(1);
        ConstWithUInt32 c3 = new ConstWithUInt32(2);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void constWithUInt32HashCode() {
        ConstWithUInt32 c1 = new ConstWithUInt32(1);
        ConstWithUInt32 c2 = new ConstWithUInt32(1);
        ConstWithUInt32 c3 = new ConstWithUInt32(2);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void constWithUInt64Equality() {
        ConstWithUInt64 c1 = new ConstWithUInt64(1);
        ConstWithUInt64 c2 = new ConstWithUInt64(1);
        ConstWithUInt64 c3 = new ConstWithUInt64(2);
        assert c1 == c2;
        assert c1 != c3;
    }

    void constWithUInt64Compare() {
        ConstWithUInt64 c1 = new ConstWithUInt64(1);
        ConstWithUInt64 c2 = new ConstWithUInt64(1);
        ConstWithUInt64 c3 = new ConstWithUInt64(2);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void constWithUInt64HashCode() {
        ConstWithUInt64 c1 = new ConstWithUInt64(1);
        ConstWithUInt64 c2 = new ConstWithUInt64(1);
        ConstWithUInt64 c3 = new ConstWithUInt64(2);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void constWithUInt128Equality() {
        ConstWithUInt128 c1 = new ConstWithUInt128(1);
        ConstWithUInt128 c2 = new ConstWithUInt128(1);
        ConstWithUInt128 c3 = new ConstWithUInt128(2);
        assert c1 == c2;
        assert c1 != c3;
    }

    void constWithUInt128Compare() {
        ConstWithUInt128 c1 = new ConstWithUInt128(1);
        ConstWithUInt128 c2 = new ConstWithUInt128(1);
        ConstWithUInt128 c3 = new ConstWithUInt128(2);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void constWithUInt128HashCode() {
        ConstWithUInt128 c1 = new ConstWithUInt128(1);
        ConstWithUInt128 c2 = new ConstWithUInt128(1);
        ConstWithUInt128 c3 = new ConstWithUInt128(2);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testClassHierarchyEqualsConstWithNoOwnProps() {
        TestC c1 = new TestC(1, "a");
        TestC c2 = new TestC(1, "a");
        TestC c3 = new TestC(2, "a");
        TestC c4 = new TestC(1, "b");
        MethodTracker.clear();
        assert c1 == c2;
        assert MethodTracker.getCount(TestA.EqualsId) == 1;
        assert c1 != c3;
        assert MethodTracker.getCount(TestA.EqualsId) == 2;
        assert c1 != c4;
        assert MethodTracker.getCount(TestA.EqualsId) == 3;
    }

    void testClassHierarchyEqualsConstWithOwnProps() {
        TestD c1 = new TestD(1, "a", 99, "x");
        TestD c2 = new TestD(1, "a", 99, "x");
        TestD c3 = new TestD(2, "a", 99, "x");
        TestD c4 = new TestD(1, "b", 99, "x");
        TestD c5 = new TestD(1, "a", 100, "x");
        TestD c6 = new TestD(1, "a", 99, "z");
        MethodTracker.clear();
        assert c1 == c2;
        assert MethodTracker.getCount(TestA.EqualsId) == 1;
        assert c1 != c3;
        assert MethodTracker.getCount(TestA.EqualsId) == 2;
        assert c1 != c4;
        assert MethodTracker.getCount(TestA.EqualsId) == 3;
        assert c1 != c5;
        assert MethodTracker.getCount(TestA.EqualsId) == 4;
        assert c1 != c6;
        assert MethodTracker.getCount(TestA.EqualsId) == 5;
    }

    void testClassHierarchyCompareConstWithNoOwnProps() {
        TestC c1 = new TestC(1, "a");
        TestC c2 = new TestC(1, "a");
        TestC c3 = new TestC(2, "a");
        TestC c4 = new TestC(1, "b");
        MethodTracker.clear();
        assert c1 <=> c2 == Equal;
        assert MethodTracker.getCount(TestA.CompareId) == 1;
        assert c1 <=> c3 == Lesser;
        assert MethodTracker.getCount(TestA.CompareId) == 2;
        assert c4 <=> c1 == Greater;
        assert MethodTracker.getCount(TestA.CompareId) == 3;
    }

    void testClassHierarchyCompareConstWithOwnProps() {
        TestD c1 = new TestD(1, "a", 99, "x");
        TestD c2 = new TestD(1, "a", 99, "x");
        TestD c3 = new TestD(2, "a", 99, "x");
        TestD c4 = new TestD(1, "b", 99, "x");
        TestD c5 = new TestD(1, "a", 100, "x");
        TestD c6 = new TestD(1, "a", 99, "z");
        MethodTracker.clear();
        assert c1 <=> c2 == Equal;
        assert MethodTracker.getCount(TestA.CompareId) == 1;
        assert c1 <=> c3 == Lesser;
        assert MethodTracker.getCount(TestA.CompareId) == 2;
        assert c4 <=> c1 == Greater;
        assert MethodTracker.getCount(TestA.CompareId) == 3;
        assert c1 <=> c5 == Lesser;
        assert MethodTracker.getCount(TestA.CompareId) == 4;
        assert c6 <=> c1 == Greater;
        assert MethodTracker.getCount(TestA.CompareId) == 5;
    }

    void testClassHierarchyHashCodeConstWithNoOwnProps() {
        TestC c1 = new TestC(1, "a");
        TestC c2 = new TestC(1, "a");
        TestC c3 = new TestC(2, "a");
        MethodTracker.clear();
        assert c1.hashCode() == c2.hashCode();
        assert MethodTracker.getCount(TestA.HashCodeId) == 2;
        assert c1.hashCode() != c3.hashCode();
        assert MethodTracker.getCount(TestA.HashCodeId) == 3; // c1.hashCode() is cached
    }

    void testClassHierarchyHashCodeConstWithOwnProps() {
        TestD c1 = new TestD(1, "a", 99, "x");
        TestD c2 = new TestD(1, "a", 99, "x");
        TestD c3 = new TestD(2, "a", 99, "x");
        TestD c4 = new TestD(1, "a", 100, "x");
        TestD c5 = new TestD(1, "a", 99, "z");
        MethodTracker.clear();
        assert c1.hashCode() == c2.hashCode();
        assert MethodTracker.getCount(TestA.HashCodeId) == 2;
        assert c1.hashCode() != c3.hashCode();
        assert MethodTracker.getCount(TestA.HashCodeId) == 3; // c1.hashCode() is cached
        assert c1.hashCode() != c4.hashCode();
        assert MethodTracker.getCount(TestA.HashCodeId) == 4; // c1.hashCode() is cached
        assert c1.hashCode() != c5.hashCode();
        assert MethodTracker.getCount(TestA.HashCodeId) == 5; // c1.hashCode() is cached
    }

    void testConstHierarchyEqualsConstWithNoOwnProps() {
        ConstC c1 = new ConstC(1, "a");
        ConstC c2 = new ConstC(1, "a");
        ConstC c3 = new ConstC(2, "a");
        ConstC c4 = new ConstC(1, "b");
        MethodTracker.clear();
        assert c1 == c2;
        assert c1 != c3;
        assert c1 != c4;
    }

    void testConstHierarchyEqualsConstWithOwnProps() {
        ConstD c1 = new ConstD(1, "a", 99, "x");
        ConstD c2 = new ConstD(1, "a", 99, "x");
        ConstD c3 = new ConstD(2, "a", 99, "x");
        ConstD c4 = new ConstD(1, "b", 99, "x");
        ConstD c5 = new ConstD(1, "a", 100, "x");
        ConstD c6 = new ConstD(1, "a", 99, "z");
        MethodTracker.clear();
        assert c1 == c2;
        assert c1 != c3;
        assert c1 != c4;
        assert c1 != c5;
        assert c1 != c6;
    }

    void testConstHierarchyCompareConstWithNoOwnProps() {
        ConstC c1 = new ConstC(1, "a");
        ConstC c2 = new ConstC(1, "a");
        ConstC c3 = new ConstC(2, "a");
        ConstC c4 = new ConstC(1, "b");
        MethodTracker.clear();
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c4 <=> c1 == Greater;
    }

    void testConstHierarchyCompareConstWithOwnProps() {
        ConstD c1 = new ConstD(1, "a", 99, "x");
        ConstD c2 = new ConstD(1, "a", 99, "x");
        ConstD c3 = new ConstD(2, "a", 99, "x");
        ConstD c4 = new ConstD(1, "b", 99, "x");
        ConstD c5 = new ConstD(1, "a", 100, "x");
        ConstD c6 = new ConstD(1, "a", 99, "z");
        MethodTracker.clear();
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c4 <=> c1 == Greater;
        assert c1 <=> c5 == Lesser;
        assert c6 <=> c1 == Greater;
    }

    void testConstHierarchyHashCodeConstWithNoOwnProps() {
        ConstC c1 = new ConstC(1, "a");
        ConstC c2 = new ConstC(1, "a");
        ConstC c3 = new ConstC(2, "a");
        MethodTracker.clear();
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstHierarchyHashCodeConstWithOwnProps() {
        ConstD c1 = new ConstD(1, "a", 99, "x");
        ConstD c2 = new ConstD(1, "a", 99, "x");
        ConstD c3 = new ConstD(2, "a", 99, "x");
        ConstD c4 = new ConstD(1, "a", 100, "x");
        ConstD c5 = new ConstD(1, "a", 99, "z");
        MethodTracker.clear();
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
        assert c1.hashCode() != c4.hashCode();
        assert c1.hashCode() != c5.hashCode();
    }

    void testConstWithNotComparableProp() {
        TestE t1 = new TestE(1);
        TestE t2 = new TestE(1);
        TestE t3 = new TestE(2);
        ConstWithNotComparableProp c1 = new ConstWithNotComparableProp(t1);
        ConstWithNotComparableProp c2 = new ConstWithNotComparableProp(t1);
        ConstWithNotComparableProp c3 = new ConstWithNotComparableProp(t2);
        ConstWithNotComparableProp c4 = new ConstWithNotComparableProp(t3);

        // c1 and c2 have the same instance of TestE so should have the same identity
        assert c1 == c2;
        // TestE does not implement Comparable so different instances should not be equal
        assert c1 != c3;
        assert c1 != c4;
    }

    void testConstWithNotOrderableProp() {
        ConstWithNotComparableProp c1 = new ConstWithNotComparableProp(new TestE(1));
        ConstWithNotComparableProp c2 = new ConstWithNotComparableProp(new TestE(1));
        ConstWithNotComparableProp c3 = new ConstWithNotComparableProp(new TestE(2));
        // TestE does not implement Orderable so will never be equal
        assert c1 <=> c2 != Equal;
        assert c1 <=> c3 != Equal;
    }

    void testConstWithNotHashableProp() {
        TestE t1 = new TestE(1);
        TestE t2 = new TestE(1);
        TestE t3 = new TestE(2);
        ConstWithNotComparableProp c1 = new ConstWithNotComparableProp(t1);
        ConstWithNotComparableProp c2 = new ConstWithNotComparableProp(t1);
        ConstWithNotComparableProp c3 = new ConstWithNotComparableProp(t2);
        ConstWithNotComparableProp c4 = new ConstWithNotComparableProp(t3);

        // Const.x doc states that a property of a const that is not Hashable should use zero for
        // its hash code. This means that all the consts will have the same hash code.
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() == c3.hashCode();
        assert c1.hashCode() == c4.hashCode();
    }

    // ----- test constants ------------------------------------------------------------------------

    const EmptyConst;

    const SimpleConst(Int n, String s);

    const ConstWithConst(SimpleConst c);

    /**
     * Non-const class used to test generation of methods in consts that extend non-const classes.
     *
     * This class has implementations of all the methods that are generated for a const, but there
     * are sub-classes below this class that implement these methods, so the methods on this class
     * should never be called.
     */
    class TestBase
            implements ecstasy.Comparable, ecstasy.collections.Hashable, ecstasy.Orderable {

        static <CompileType extends TestBase> Boolean equals(CompileType value1, CompileType value2) {
            assert as "Should not have been called";
        }

        static <CompileType extends TestBase> Ordered compare(CompileType value1, CompileType value2) {
            assert as "Should not have been called";
        }

        static <CompileType extends TestBase> Int hashCode(CompileType value1) {
            assert as "Should not have been called";
        }
    }

    /**
     * Non-const class used to test generation of methods in consts that extend non-const classes.
     *
     * This class has implementations of all the methods that are generated for a const, so these
     * methods should be called by consts that extend this class.
     */
    class TestA(Int128 n)
            extends TestBase
            implements ecstasy.Comparable, ecstasy.collections.Hashable, ecstasy.Orderable {

        static Int EqualsId = 1;
        static Int CompareId = 2;
        static Int HashCodeId = 3;

        static <CompileType extends TestA> Boolean equals(CompileType value1, CompileType value2) {
            MethodTracker.called(EqualsId);
            return value1.n == value2.n;
        }

        static <CompileType extends TestA> Ordered compare(CompileType value1, CompileType value2) {
            MethodTracker.called(CompareId);
            return value1.n <=> value2.n;
        }

        static <CompileType extends TestA> Int hashCode(CompileType value1) {
            MethodTracker.called(HashCodeId);
            return value1.n.toInt64();
        }
    }

    /**
     * Non-const class used to test generation of methods in consts that extend non-const classes.
     *
     * This class has none of the methods generated for a const, so const sub classes of this class
     * should include this class's properties in the generated methods.
     */
    class TestB(Int128 n, String s) extends TestA(n) {
    }

    /**
     * A const that extends a non-const class and has no properties of its own.
     */
    const TestC(Int128 n, String s) extends TestB(n, s) {
    }

    /**
     * A const that extends a non-const class and has properties of its own.
     */
    const TestD(Int128 n, String s, Int i, String t) extends TestB(n, s) {
    }

    class TestE(Int n)
            implements Freezable {
        @Override
        immutable TestE freeze(Boolean inPlace = False) {
            if (this.is(immutable)) {
                return this;
            }
            if (inPlace) {
                return this.makeImmutable();
            }
            return new TestE(n).makeImmutable();
        }
    }

    service ServiceA
            implements Orderable, Hashable {

        static Int EqualsId = 1;
        static Int CompareId = 2;
        static Int HashCodeId = 3;

        static <CompileType extends ServiceA> Boolean equals(CompileType value1, CompileType value2) {
            MethodTracker.called(EqualsId);
            return True;
        }

        static <CompileType extends ServiceA> Ordered compare(CompileType value1, CompileType value2) {
            MethodTracker.called(CompareId);
            return Equal;
        }

        static <CompileType extends ServiceA> Int hashCode(CompileType value1) {
            MethodTracker.called(HashCodeId);
            return 99;
        }
    }

    const ConstWithNotComparableProp(TestE e);

    const ConstWithService(ServiceA svc);

    const ConstA(Int n);

    const ConstB(Int n, String s) extends ConstA(n);

    const ConstC(Int n, String s) extends ConstB(n, s);

    const ConstD(Int n, String s, Int i, String t) extends ConstB(n, s);

    const ConstWithBit(Bit b);
    const ConstWithBoolean(Boolean b);
    const ConstWithChar(Char c);
    const ConstWithDec32(Dec32 d);
    const ConstWithDec64(Dec64 d);
    const ConstWithFloat32(Float32 f);
    const ConstWithFloat64(Float64 f);
    const ConstWithInt8(Int8 i);
    const ConstWithInt16(Int16 i);
    const ConstWithInt32(Int32 i);
    const ConstWithInt64(Int64 i);
    const ConstWithInt128(Int128 n);
    const ConstWithNibble(Nibble n);
    const ConstWithString(String s);
    const ConstWithUInt8(UInt8 i);
    const ConstWithUInt16(UInt16 i);
    const ConstWithUInt32(UInt32 i);
    const ConstWithUInt64(UInt64 i);
    const ConstWithUInt128(UInt128 n);

    /**
     * A service to help track method calls during tests.console
     */
    static service MethodTracker {

        Array<Int>? methodCounts;

        void called(Int id) {
            Array<Int>? array = this.methodCounts;
            if (array == Null) {
                array = new Array();
                array.add(id);
                methodCounts = array;
            } else {
                array.as(Array<Int>).add(id);
            }
        }

        Int getCount(Int id) {
            Array<Int>? array = this.methodCounts;
            if (array == Null) {
                return 0;
            }
            Int count = 0;
            Int[] counts = array.as(Array<Int>);
            for (Int i = 0; i < counts.size; i++) {
                if (counts[i] == id) {
                    count++;
                }
            }
            return count;
        }

        void clear() {
            methodCounts = new Array();
        }
    }
}