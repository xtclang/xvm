package constTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running Const Tests >>>>");

        testEmptyConstShouldBeEqual();
        testEmptyConstShouldBeOrderable();
        testEmptyConstShouldHaveNoneZeroHashCode();
        testEmptyConstShouldBeStringable();

        testSimpleConstEquality();
        testSimpleConstHashCode();
        testSimpleTestConstCompare();
        testSimpleConstShouldBeStringable();

        testConstWithConstEquality();
        testConstWithConstHashCode();
        testConstWithConstCompare();
        testConstWithConstShouldBeStringable();

        testConstWithBitEquality();
        testConstWithBitCompare();
        testConstWithBitHashCode();
        testConstWithBitShouldBeStringable();

        testConstWithBooleanEquality();
        testConstWithBooleanCompare();
        testConstWithBooleanHashCode();
        testConstWithBooleanShouldBeStringable();

        testConstWithCharEquality();
        testConstWithCharCompare();
        testConstWithCharHashCode();
        testConstWithCharShouldBeStringable();

        testConstWithDec32Equality();
        testConstWithDec32Compare();
        testConstWithDec32HashCode();
        testConstWithDec32ShouldBeStringable();

        testConstWithDec64Equality();
        testConstWithDec64Compare();
        testConstWithDec64HashCode();
        testConstWithDec64ShouldBeStringable();

        testConstWithFloat32Equality();
        testConstWithFloat32Compare();
        testConstWithFloat32HashCode();
        testConstWithFloat32ShouldBeStringable();

        testConstWithFloat64Equality();
        testConstWithFloat64Compare();
        testConstWithFloat64HashCode();
        testConstWithFloat64ShouldBeStringable();

        testConstWithInt8Equality();
        testConstWithInt8Compare();
        testConstWithInt8HashCode();
        testConstWithInt8ShouldBeStringable();

        testConstWithInt16Equality();
        testConstWithInt16Compare();
        testConstWithInt16HashCode();
        testConstWithInt16ShouldBeStringable();

        testConstWithInt32Equality();
        testConstWithInt32Compare();
        testConstWithInt32HashCode();
        testConstWithInt32ShouldBeStringable();

        testConstWithInt64Equality();
        testConstWithInt64Compare();
        testConstWithInt64HashCode();
        testConstWithInt64ShouldBeStringable();

        testConstWithInt128Equality();
        testConstWithInt128Compare();
        testConstWithInt128HashCode();
        testConstWithInt128ShouldBeStringable();

        testConstWithNibbleEquality();
        testConstWithNibbleCompare();
        testConstWithNibbleHashCode();
        testConstWithNibbleShouldBeStringable();

        testConstWithStringEquality();
        testConstWithStringCompare();
        testConstWithStringHashCode();
        testConstWithStringShouldBeStringable();

        testConstWithUInt8Equality();
        testConstWithUInt8Compare();
        testConstWithUInt8HashCode();
        testConstWithUInt8ShouldBeStringable();

        testConstWithUInt16Equality();
        testConstWithUInt16Compare();
        testConstWithUInt16HashCode();
        testConstWithUInt16ShouldBeStringable();

        testConstWithUInt32Equality();
        testConstWithUInt32Compare();
        testConstWithUInt32HashCode();
        testConstWithUInt32ShouldBeStringable();

        testConstWithUInt64Equality();
        testConstWithUInt64Compare();
        testConstWithUInt64HashCode();
        testConstWithUInt64ShouldBeStringable();

        testConstWithUInt128Equality();
        testConstWithUInt128Compare();
        testConstWithUInt128HashCode();
        testConstWithUInt128ShouldBeStringable();

        // +++ TODO: these tests fail under the interpreter
        testClassHierarchyEqualsConstWithNoOwnProps();
        testClassHierarchyEqualsConstWithOwnProps();
        testClassHierarchyCompareConstWithNoOwnProps();
        testClassHierarchyCompareConstWithOwnProps();
        testClassHierarchyHashCodeConstWithNoOwnProps();
        testClassHierarchyHashCodeConstWithOwnProps();
        testConstHierarchyWithNoOwnPropsIsStringable();
        // ---

        testConstHierarchyEqualsConstWithNoOwnProps();
        testConstHierarchyEqualsConstWithOwnProps();
        testConstHierarchyCompareConstWithNoOwnProps();
        testConstHierarchyCompareConstWithOwnProps();
        testConstHierarchyHashCodeConstWithNoOwnProps();
        testConstHierarchyHashCodeConstWithOwnProps();
        testConstHierarchyWithWithOwnPropsIsStringable();

        // +++ TODO: these tests fail under the interpreter
        testTestConstWithNotComparableProp();
        testConstWithNotOrderableProp();
        testConstWithNotHashableProp();
        // ---

        testConstWithServiceIsStringable();
        testConstWithNullablePropIsStringable();

        console.print("<<<< Finished Const Tests <<<<<");
    }

    void testEmptyConstShouldBeEqual() {
        TestEmptyConst c1 = new TestEmptyConst();
        TestEmptyConst c2 = new TestEmptyConst();
        assert c1 == c2;
    }

    void testEmptyConstShouldBeOrderable() {
        TestEmptyConst c1 = new TestEmptyConst();
        TestEmptyConst c2 = new TestEmptyConst();
        assert c1 <=> c2 == Equal;
    }

    void testEmptyConstShouldHaveNoneZeroHashCode() {
        TestEmptyConst c1 = new TestEmptyConst();
        TestEmptyConst c2 = new TestEmptyConst();
        assert c1.hashCode() != 0;
        assert c2.hashCode() != 0;
        assert c1.hashCode() == c2.hashCode();
    }

    void testEmptyConstShouldBeStringable() {
        TestEmptyConst c1 = new TestEmptyConst();
        assert c1.estimateStringLength() == 2;
        assert c1.toString() == "()";
    }

    void testSimpleConstEquality() {
        TestSimpleConst c1 = new TestSimpleConst(1, "a");
        TestSimpleConst c2 = new TestSimpleConst(1, "a");
        TestSimpleConst c3 = new TestSimpleConst(2, "a");
        TestSimpleConst c4 = new TestSimpleConst(1, "b");
        assert c1 == c2;
        assert c1 != c3;
        assert c1 != c4;
    }

    void testSimpleConstHashCode() {
        TestSimpleConst c1 = new TestSimpleConst(1, "a");
        TestSimpleConst c2 = new TestSimpleConst(1, "a");
        TestSimpleConst c3 = new TestSimpleConst(2, "a");
        TestSimpleConst c4 = new TestSimpleConst(1, "b");
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
        assert c1.hashCode() != c4.hashCode();
    }

    void testSimpleTestConstCompare() {
        TestSimpleConst c1 = new TestSimpleConst(1, "a");
        TestSimpleConst c2 = new TestSimpleConst(1, "a");
        TestSimpleConst c3 = new TestSimpleConst(2, "a");
        TestSimpleConst c4 = new TestSimpleConst(1, "b");
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c1 <=> c4 == Lesser;
        assert c3 <=> c2 == Greater;
        assert c4 <=> c1 == Greater;
    }

    void testSimpleConstShouldBeStringable() {
        TestSimpleConst c1 = new TestSimpleConst(1, "a");
        assert c1.estimateStringLength() == 10;
        assert c1.toString() == "(n=1, s=a)";
    }

    void testConstWithConstEquality() {
        TestConstWithConst c1 = new TestConstWithConst(new TestSimpleConst(1, "a"));
        TestConstWithConst c2 = new TestConstWithConst(new TestSimpleConst(1, "a"));
        TestConstWithConst c3 = new TestConstWithConst(new TestSimpleConst(2, "a"));
        TestConstWithConst c4 = new TestConstWithConst(new TestSimpleConst(1, "b"));
        assert c1 == c2;
        assert c1 != c3;
        assert c1 != c4;
    }

    void testConstWithConstHashCode() {
        TestConstWithConst c1 = new TestConstWithConst(new TestSimpleConst(1, "a"));
        TestConstWithConst c2 = new TestConstWithConst(new TestSimpleConst(1, "a"));
        TestConstWithConst c3 = new TestConstWithConst(new TestSimpleConst(2, "a"));
        TestConstWithConst c4 = new TestConstWithConst(new TestSimpleConst(1, "b"));
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
        assert c1.hashCode() != c4.hashCode();
    }

    void testConstWithConstCompare() {
        TestConstWithConst c1 = new TestConstWithConst(new TestSimpleConst(1, "a"));
        TestConstWithConst c2 = new TestConstWithConst(new TestSimpleConst(1, "a"));
        TestConstWithConst c3 = new TestConstWithConst(new TestSimpleConst(2, "a"));
        TestConstWithConst c4 = new TestConstWithConst(new TestSimpleConst(1, "b"));
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c1 <=> c4 == Lesser;
        assert c3 <=> c2 == Greater;
        assert c4 <=> c1 == Greater;
    }

    void testConstWithConstShouldBeStringable() {
        TestConstWithConst c1 = new TestConstWithConst(new TestSimpleConst(1, "a"));
        assert c1.estimateStringLength() == 14;
        assert c1.toString() == "(c=(n=1, s=a))";
    }

    void testConstWithBitEquality() {
        TestConstWithBit c1 = new TestConstWithBit(0);
        TestConstWithBit c2 = new TestConstWithBit(0);
        TestConstWithBit c3 = new TestConstWithBit(1);
        assert c1 == c2;
        assert c1 != c3;
    }

    void testConstWithBitCompare() {
        TestConstWithBit c1 = new TestConstWithBit(0);
        TestConstWithBit c2 = new TestConstWithBit(0);
        TestConstWithBit c3 = new TestConstWithBit(1);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void testConstWithBitHashCode() {
        TestConstWithBit c1 = new TestConstWithBit(0);
        TestConstWithBit c2 = new TestConstWithBit(0);
        TestConstWithBit c3 = new TestConstWithBit(1);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstWithBitShouldBeStringable() {
        TestConstWithBit c1 = new TestConstWithBit(0);
        assert c1.estimateStringLength() == 5;
        assert c1.toString() == "(b=0)";
        TestConstWithBit c2 = new TestConstWithBit(1);
        assert c2.estimateStringLength() == 5;
        assert c2.toString() == "(b=1)";
    }

    void testConstWithBooleanEquality() {
        TestConstWithBoolean c1 = new TestConstWithBoolean(False);
        TestConstWithBoolean c2 = new TestConstWithBoolean(False);
        TestConstWithBoolean c3 = new TestConstWithBoolean(True);
        assert c1 == c2;
        assert c1 != c3;
    }

    void testConstWithBooleanCompare() {
        TestConstWithBoolean c1 = new TestConstWithBoolean(False);
        TestConstWithBoolean c2 = new TestConstWithBoolean(False);
        TestConstWithBoolean c3 = new TestConstWithBoolean(True);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void testConstWithBooleanHashCode() {
        TestConstWithBoolean c1 = new TestConstWithBoolean(False);
        TestConstWithBoolean c2 = new TestConstWithBoolean(False);
        TestConstWithBoolean c3 = new TestConstWithBoolean(True);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstWithBooleanShouldBeStringable() {
        TestConstWithBoolean c1 = new TestConstWithBoolean(True);
        assert c1.estimateStringLength() == 8;
        assert c1.toString() == "(b=True)";
        TestConstWithBoolean c2 = new TestConstWithBoolean(False);
        assert c2.estimateStringLength() == 9;
        assert c2.toString() == "(b=False)";
    }

    void testConstWithCharEquality() {
        TestConstWithChar c1 = new TestConstWithChar('a');
        TestConstWithChar c2 = new TestConstWithChar('a');
        TestConstWithChar c3 = new TestConstWithChar('b');
        assert c1 == c2;
        assert c1 != c3;
    }

    void testConstWithCharCompare() {
        TestConstWithChar c1 = new TestConstWithChar('a');
        TestConstWithChar c2 = new TestConstWithChar('a');
        TestConstWithChar c3 = new TestConstWithChar('b');
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void testConstWithCharHashCode() {
        TestConstWithChar c1 = new TestConstWithChar('a');
        TestConstWithChar c2 = new TestConstWithChar('a');
        TestConstWithChar c3 = new TestConstWithChar('b');
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstWithCharShouldBeStringable() {
        TestConstWithChar c1 = new TestConstWithChar('a');
        assert c1.estimateStringLength() == 5;
        assert c1.toString() == "(c=a)";
    }

    void testConstWithDec32Equality() {
        TestConstWithDec32 c1 = new TestConstWithDec32(1.0);
        TestConstWithDec32 c2 = new TestConstWithDec32(1.0);
        TestConstWithDec32 c3 = new TestConstWithDec32(2.0);
        assert c1 == c2;
        assert c1 != c3;
    }

    void testConstWithDec32Compare() {
        TestConstWithDec32 c1 = new TestConstWithDec32(1.0);
        TestConstWithDec32 c2 = new TestConstWithDec32(1.0);
        TestConstWithDec32 c3 = new TestConstWithDec32(2.0);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void testConstWithDec32HashCode() {
        TestConstWithDec32 c1 = new TestConstWithDec32(1.0);
        TestConstWithDec32 c2 = new TestConstWithDec32(1.0);
        TestConstWithDec32 c3 = new TestConstWithDec32(2.0);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstWithDec32ShouldBeStringable() {
        TestConstWithDec32 c1 = new TestConstWithDec32(1.5);
        assert c1.estimateStringLength() == 7;
        assert c1.toString() == "(d=1.5)";
    }

    void testConstWithDec64Equality() {
        TestConstWithDec64 c1 = new TestConstWithDec64(1.0);
        TestConstWithDec64 c2 = new TestConstWithDec64(1.0);
        TestConstWithDec64 c3 = new TestConstWithDec64(2.0);
        assert c1 == c2;
        assert c1 != c3;
    }

    void testConstWithDec64Compare() {
        TestConstWithDec64 c1 = new TestConstWithDec64(1.0);
        TestConstWithDec64 c2 = new TestConstWithDec64(1.0);
        TestConstWithDec64 c3 = new TestConstWithDec64(2.0);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void testConstWithDec64HashCode() {
        TestConstWithDec64 c1 = new TestConstWithDec64(1.0);
        TestConstWithDec64 c2 = new TestConstWithDec64(1.0);
        TestConstWithDec64 c3 = new TestConstWithDec64(2.0);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstWithDec64ShouldBeStringable() {
        TestConstWithDec64 c1 = new TestConstWithDec64(1.5);
        assert c1.estimateStringLength() == 7;
        assert c1.toString() == "(d=1.5)";
    }

    void testConstWithFloat32Equality() {
        TestConstWithFloat32 c1 = new TestConstWithFloat32(1.0);
        TestConstWithFloat32 c2 = new TestConstWithFloat32(1.0);
        TestConstWithFloat32 c3 = new TestConstWithFloat32(2.0);
        assert c1 == c2;
        assert c1 != c3;
    }

    void testConstWithFloat32Compare() {
        TestConstWithFloat32 c1 = new TestConstWithFloat32(1.0);
        TestConstWithFloat32 c2 = new TestConstWithFloat32(1.0);
        TestConstWithFloat32 c3 = new TestConstWithFloat32(2.0);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void testConstWithFloat32HashCode() {
        TestConstWithFloat32 c1 = new TestConstWithFloat32(1.0);
        TestConstWithFloat32 c2 = new TestConstWithFloat32(1.0);
        TestConstWithFloat32 c3 = new TestConstWithFloat32(2.0);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstWithFloat32ShouldBeStringable() {
        TestConstWithFloat32 c1 = new TestConstWithFloat32(1.5);
        assert c1.estimateStringLength() == 7;
        assert c1.toString() == "(f=1.5)";
    }

    void testConstWithFloat64Equality() {
        TestConstWithFloat64 c1 = new TestConstWithFloat64(1.0);
        TestConstWithFloat64 c2 = new TestConstWithFloat64(1.0);
        TestConstWithFloat64 c3 = new TestConstWithFloat64(2.0);
        assert c1 == c2;
        assert c1 != c3;
    }

    void testConstWithFloat64Compare() {
        TestConstWithFloat64 c1 = new TestConstWithFloat64(1.0);
        TestConstWithFloat64 c2 = new TestConstWithFloat64(1.0);
        TestConstWithFloat64 c3 = new TestConstWithFloat64(2.0);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void testConstWithFloat64HashCode() {
        TestConstWithFloat64 c1 = new TestConstWithFloat64(1.0);
        TestConstWithFloat64 c2 = new TestConstWithFloat64(1.0);
        TestConstWithFloat64 c3 = new TestConstWithFloat64(2.0);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstWithFloat64ShouldBeStringable() {
        TestConstWithFloat64 c1 = new TestConstWithFloat64(1.5);
        assert c1.estimateStringLength() == 7;
        assert c1.toString() == "(f=1.5)";
    }

    void testConstWithInt8Equality() {
        TestConstWithInt8 c1 = new TestConstWithInt8(1);
        TestConstWithInt8 c2 = new TestConstWithInt8(1);
        TestConstWithInt8 c3 = new TestConstWithInt8(2);
        assert c1 == c2;
        assert c1 != c3;
    }

    void testConstWithInt8Compare() {
        TestConstWithInt8 c1 = new TestConstWithInt8(1);
        TestConstWithInt8 c2 = new TestConstWithInt8(1);
        TestConstWithInt8 c3 = new TestConstWithInt8(2);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void testConstWithInt8HashCode() {
        TestConstWithInt8 c1 = new TestConstWithInt8(1);
        TestConstWithInt8 c2 = new TestConstWithInt8(1);
        TestConstWithInt8 c3 = new TestConstWithInt8(2);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstWithInt8ShouldBeStringable() {
        TestConstWithInt8 c1 = new TestConstWithInt8(1);
        assert c1.estimateStringLength() == 5;
        assert c1.toString() == "(i=1)";
    }

    void testConstWithInt16Equality() {
        TestConstWithInt16 c1 = new TestConstWithInt16(1);
        TestConstWithInt16 c2 = new TestConstWithInt16(1);
        TestConstWithInt16 c3 = new TestConstWithInt16(2);
        assert c1 == c2;
        assert c1 != c3;
    }

    void testConstWithInt16Compare() {
        TestConstWithInt16 c1 = new TestConstWithInt16(1);
        TestConstWithInt16 c2 = new TestConstWithInt16(1);
        TestConstWithInt16 c3 = new TestConstWithInt16(2);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void testConstWithInt16HashCode() {
        TestConstWithInt16 c1 = new TestConstWithInt16(1);
        TestConstWithInt16 c2 = new TestConstWithInt16(1);
        TestConstWithInt16 c3 = new TestConstWithInt16(2);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstWithInt16ShouldBeStringable() {
        TestConstWithInt16 c1 = new TestConstWithInt16(1);
        assert c1.estimateStringLength() == 5;
        assert c1.toString() == "(i=1)";
    }

    void testConstWithInt32Equality() {
        TestConstWithInt32 c1 = new TestConstWithInt32(1);
        TestConstWithInt32 c2 = new TestConstWithInt32(1);
        TestConstWithInt32 c3 = new TestConstWithInt32(2);
        assert c1 == c2;
        assert c1 != c3;
    }

    void testConstWithInt32Compare() {
        TestConstWithInt32 c1 = new TestConstWithInt32(1);
        TestConstWithInt32 c2 = new TestConstWithInt32(1);
        TestConstWithInt32 c3 = new TestConstWithInt32(2);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void testConstWithInt32HashCode() {
        TestConstWithInt32 c1 = new TestConstWithInt32(1);
        TestConstWithInt32 c2 = new TestConstWithInt32(1);
        TestConstWithInt32 c3 = new TestConstWithInt32(2);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstWithInt32ShouldBeStringable() {
        TestConstWithInt32 c1 = new TestConstWithInt32(1);
        assert c1.estimateStringLength() == 5;
        assert c1.toString() == "(i=1)";
    }

    void testConstWithInt64Equality() {
        TestConstWithInt64 c1 = new TestConstWithInt64(1);
        TestConstWithInt64 c2 = new TestConstWithInt64(1);
        TestConstWithInt64 c3 = new TestConstWithInt64(2);
        assert c1 == c2;
        assert c1 != c3;
    }

    void testConstWithInt64Compare() {
        TestConstWithInt64 c1 = new TestConstWithInt64(1);
        TestConstWithInt64 c2 = new TestConstWithInt64(1);
        TestConstWithInt64 c3 = new TestConstWithInt64(2);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void testConstWithInt64HashCode() {
        TestConstWithInt64 c1 = new TestConstWithInt64(1);
        TestConstWithInt64 c2 = new TestConstWithInt64(1);
        TestConstWithInt64 c3 = new TestConstWithInt64(2);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstWithInt64ShouldBeStringable() {
        TestConstWithInt64 c1 = new TestConstWithInt64(1);
        assert c1.estimateStringLength() == 5;
        assert c1.toString() == "(i=1)";
    }

    void testConstWithInt128Equality() {
        TestConstWithInt128 c1 = new TestConstWithInt128(1);
        TestConstWithInt128 c2 = new TestConstWithInt128(1);
        TestConstWithInt128 c3 = new TestConstWithInt128(2);
        assert c1 == c2;
        assert c1 != c3;
    }

    void testConstWithInt128Compare() {
        TestConstWithInt128 c1 = new TestConstWithInt128(1);
        TestConstWithInt128 c2 = new TestConstWithInt128(1);
        TestConstWithInt128 c3 = new TestConstWithInt128(2);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void testConstWithInt128HashCode() {
        TestConstWithInt128 c1 = new TestConstWithInt128(1);
        TestConstWithInt128 c2 = new TestConstWithInt128(1);
        TestConstWithInt128 c3 = new TestConstWithInt128(2);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstWithInt128ShouldBeStringable() {
        TestConstWithInt128 c1 = new TestConstWithInt128(1);
        assert c1.estimateStringLength() == 5;
        assert c1.toString() == "(n=1)";
    }

    void testConstWithNibbleEquality() {
        TestConstWithNibble c1 = new TestConstWithNibble(0);
        TestConstWithNibble c2 = new TestConstWithNibble(0);
        TestConstWithNibble c3 = new TestConstWithNibble(1);
        assert c1 == c2;
        assert c1 != c3;
    }

    void testConstWithNibbleCompare() {
        TestConstWithNibble c1 = new TestConstWithNibble(0);
        TestConstWithNibble c2 = new TestConstWithNibble(0);
        TestConstWithNibble c3 = new TestConstWithNibble(1);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void testConstWithNibbleHashCode() {
        TestConstWithNibble c1 = new TestConstWithNibble(0);
        TestConstWithNibble c2 = new TestConstWithNibble(0);
        TestConstWithNibble c3 = new TestConstWithNibble(1);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstWithNibbleShouldBeStringable() {
        TestConstWithNibble c1 = new TestConstWithNibble(1);
        assert c1.estimateStringLength() == 5;
        assert c1.toString() == "(n=1)";
    }

    void testConstWithStringEquality() {
        TestConstWithString c1 = new TestConstWithString("a");
        TestConstWithString c2 = new TestConstWithString("a");
        TestConstWithString c3 = new TestConstWithString("b");
        assert c1 == c2;
        assert c1 != c3;
    }

    void testConstWithStringCompare() {
        TestConstWithString c1 = new TestConstWithString("a");
        TestConstWithString c2 = new TestConstWithString("a");
        TestConstWithString c3 = new TestConstWithString("b");
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void testConstWithStringHashCode() {
        TestConstWithString c1 = new TestConstWithString("a");
        TestConstWithString c2 = new TestConstWithString("a");
        TestConstWithString c3 = new TestConstWithString("b");
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstWithStringShouldBeStringable() {
        TestConstWithString c1 = new TestConstWithString("abc");
        assert c1.estimateStringLength() == 7;
        assert c1.toString() == "(s=abc)";
    }

    void testConstWithUInt8Equality() {
        TestConstWithUInt8 c1 = new TestConstWithUInt8(1);
        TestConstWithUInt8 c2 = new TestConstWithUInt8(1);
        TestConstWithUInt8 c3 = new TestConstWithUInt8(2);
        assert c1 == c2;
        assert c1 != c3;
    }

    void testConstWithUInt8Compare() {
        TestConstWithUInt8 c1 = new TestConstWithUInt8(1);
        TestConstWithUInt8 c2 = new TestConstWithUInt8(1);
        TestConstWithUInt8 c3 = new TestConstWithUInt8(2);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void testConstWithUInt8HashCode() {
        TestConstWithUInt8 c1 = new TestConstWithUInt8(1);
        TestConstWithUInt8 c2 = new TestConstWithUInt8(1);
        TestConstWithUInt8 c3 = new TestConstWithUInt8(2);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstWithUInt8ShouldBeStringable() {
        TestConstWithUInt8 c1 = new TestConstWithUInt8(1);
        assert c1.estimateStringLength() == 5;
        assert c1.toString() == "(i=1)";
    }

    void testConstWithUInt16Equality() {
        TestConstWithUInt16 c1 = new TestConstWithUInt16(1);
        TestConstWithUInt16 c2 = new TestConstWithUInt16(1);
        TestConstWithUInt16 c3 = new TestConstWithUInt16(2);
        assert c1 == c2;
        assert c1 != c3;
    }

    void testConstWithUInt16Compare() {
        TestConstWithUInt16 c1 = new TestConstWithUInt16(1);
        TestConstWithUInt16 c2 = new TestConstWithUInt16(1);
        TestConstWithUInt16 c3 = new TestConstWithUInt16(2);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void testConstWithUInt16HashCode() {
        TestConstWithUInt16 c1 = new TestConstWithUInt16(1);
        TestConstWithUInt16 c2 = new TestConstWithUInt16(1);
        TestConstWithUInt16 c3 = new TestConstWithUInt16(2);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstWithUInt16ShouldBeStringable() {
        TestConstWithUInt16 c1 = new TestConstWithUInt16(1);
        assert c1.estimateStringLength() == 5;
        assert c1.toString() == "(i=1)";
    }

    void testConstWithUInt32Equality() {
        TestConstWithUInt32 c1 = new TestConstWithUInt32(1);
        TestConstWithUInt32 c2 = new TestConstWithUInt32(1);
        TestConstWithUInt32 c3 = new TestConstWithUInt32(2);
        assert c1 == c2;
        assert c1 != c3;
    }

    void testConstWithUInt32Compare() {
        TestConstWithUInt32 c1 = new TestConstWithUInt32(1);
        TestConstWithUInt32 c2 = new TestConstWithUInt32(1);
        TestConstWithUInt32 c3 = new TestConstWithUInt32(2);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void testConstWithUInt32HashCode() {
        TestConstWithUInt32 c1 = new TestConstWithUInt32(1);
        TestConstWithUInt32 c2 = new TestConstWithUInt32(1);
        TestConstWithUInt32 c3 = new TestConstWithUInt32(2);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstWithUInt32ShouldBeStringable() {
        TestConstWithUInt32 c1 = new TestConstWithUInt32(1);
        assert c1.estimateStringLength() == 5;
        assert c1.toString() == "(i=1)";
    }

    void testConstWithUInt64Equality() {
        TestConstWithUInt64 c1 = new TestConstWithUInt64(1);
        TestConstWithUInt64 c2 = new TestConstWithUInt64(1);
        TestConstWithUInt64 c3 = new TestConstWithUInt64(2);
        assert c1 == c2;
        assert c1 != c3;
    }

    void testConstWithUInt64Compare() {
        TestConstWithUInt64 c1 = new TestConstWithUInt64(1);
        TestConstWithUInt64 c2 = new TestConstWithUInt64(1);
        TestConstWithUInt64 c3 = new TestConstWithUInt64(2);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void testConstWithUInt64HashCode() {
        TestConstWithUInt64 c1 = new TestConstWithUInt64(1);
        TestConstWithUInt64 c2 = new TestConstWithUInt64(1);
        TestConstWithUInt64 c3 = new TestConstWithUInt64(2);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstWithUInt64ShouldBeStringable() {
        TestConstWithUInt64 c1 = new TestConstWithUInt64(1);
        assert c1.estimateStringLength() == 5;
        assert c1.toString() == "(i=1)";
    }

    void testConstWithUInt128Equality() {
        TestConstWithUInt128 c1 = new TestConstWithUInt128(1);
        TestConstWithUInt128 c2 = new TestConstWithUInt128(1);
        TestConstWithUInt128 c3 = new TestConstWithUInt128(2);
        assert c1 == c2;
        assert c1 != c3;
    }

    void testConstWithUInt128Compare() {
        TestConstWithUInt128 c1 = new TestConstWithUInt128(1);
        TestConstWithUInt128 c2 = new TestConstWithUInt128(1);
        TestConstWithUInt128 c3 = new TestConstWithUInt128(2);
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c3 <=> c2 == Greater;
    }

    void testConstWithUInt128HashCode() {
        TestConstWithUInt128 c1 = new TestConstWithUInt128(1);
        TestConstWithUInt128 c2 = new TestConstWithUInt128(1);
        TestConstWithUInt128 c3 = new TestConstWithUInt128(2);
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstWithUInt128ShouldBeStringable() {
        TestConstWithUInt128 c1 = new TestConstWithUInt128(1);
        assert c1.estimateStringLength() == 5;
        assert c1.toString() == "(n=1)";
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
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testClassHierarchyHashCodeConstWithOwnProps() {
        TestD c1 = new TestD(1, "a", 99, "x");
        TestD c2 = new TestD(1, "a", 99, "x");
        TestD c3 = new TestD(2, "a", 99, "x");
        TestD c4 = new TestD(1, "a", 100, "x");
        TestD c5 = new TestD(1, "a", 99, "z");
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
        assert c1.hashCode() != c4.hashCode();
        assert c1.hashCode() != c5.hashCode();
    }

    void testConstHierarchyEqualsConstWithNoOwnProps() {
        TestConstC c1 = new TestConstC(1, "a");
        TestConstC c2 = new TestConstC(1, "a");
        TestConstC c3 = new TestConstC(2, "a");
        TestConstC c4 = new TestConstC(1, "b");
        MethodTracker.clear();
        assert c1 == c2;
        assert c1 != c3;
        assert c1 != c4;
    }

    void testConstHierarchyWithNoOwnPropsIsStringable() {
        TestConstC c1 = new TestConstC(2, "a");
        assert c1.estimateStringLength() == 10;
        assert c1.toString() == "(n=2, s=a)";
    }

    void testConstHierarchyEqualsConstWithOwnProps() {
        TestConstD c1 = new TestConstD(1, "a", 99, "x");
        TestConstD c2 = new TestConstD(1, "a", 99, "x");
        TestConstD c3 = new TestConstD(2, "a", 99, "x");
        TestConstD c4 = new TestConstD(1, "b", 99, "x");
        TestConstD c5 = new TestConstD(1, "a", 100, "x");
        TestConstD c6 = new TestConstD(1, "a", 99, "z");
        MethodTracker.clear();
        assert c1 == c2;
        assert c1 != c3;
        assert c1 != c4;
        assert c1 != c5;
        assert c1 != c6;
    }

    void testConstHierarchyCompareConstWithNoOwnProps() {
        TestConstC c1 = new TestConstC(1, "a");
        TestConstC c2 = new TestConstC(1, "a");
        TestConstC c3 = new TestConstC(2, "a");
        TestConstC c4 = new TestConstC(1, "b");
        MethodTracker.clear();
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c4 <=> c1 == Greater;
    }

    void testConstHierarchyCompareConstWithOwnProps() {
        TestConstD c1 = new TestConstD(1, "a", 99, "x");
        TestConstD c2 = new TestConstD(1, "a", 99, "x");
        TestConstD c3 = new TestConstD(2, "a", 99, "x");
        TestConstD c4 = new TestConstD(1, "b", 99, "x");
        TestConstD c5 = new TestConstD(1, "a", 100, "x");
        TestConstD c6 = new TestConstD(1, "a", 99, "z");
        MethodTracker.clear();
        assert c1 <=> c2 == Equal;
        assert c1 <=> c3 == Lesser;
        assert c4 <=> c1 == Greater;
        assert c1 <=> c5 == Lesser;
        assert c6 <=> c1 == Greater;
    }

    void testConstHierarchyHashCodeConstWithNoOwnProps() {
        TestConstC c1 = new TestConstC(1, "a");
        TestConstC c2 = new TestConstC(1, "a");
        TestConstC c3 = new TestConstC(2, "a");
        MethodTracker.clear();
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
    }

    void testConstHierarchyHashCodeConstWithOwnProps() {
        TestConstD c1 = new TestConstD(1, "a", 99, "x");
        TestConstD c2 = new TestConstD(1, "a", 99, "x");
        TestConstD c3 = new TestConstD(2, "a", 99, "x");
        TestConstD c4 = new TestConstD(1, "a", 100, "x");
        TestConstD c5 = new TestConstD(1, "a", 99, "z");
        MethodTracker.clear();
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() != c3.hashCode();
        assert c1.hashCode() != c4.hashCode();
        assert c1.hashCode() != c5.hashCode();
    }

    void testConstHierarchyWithWithOwnPropsIsStringable() {
        TestConstD c1 = new TestConstD(2, "a", 99, "x");
        assert c1.estimateStringLength() == 21;
        assert c1.toString() == "(n=2, s=a, i=99, t=x)";
    }

    void testTestConstWithNotComparableProp() {
        TestE t1 = new TestE(1);
        TestE t2 = new TestE(1);
        TestE t3 = new TestE(2);
        TestConstWithNotComparableProp c1 = new TestConstWithNotComparableProp(t1);
        TestConstWithNotComparableProp c2 = new TestConstWithNotComparableProp(t1);
        TestConstWithNotComparableProp c3 = new TestConstWithNotComparableProp(t2);
        TestConstWithNotComparableProp c4 = new TestConstWithNotComparableProp(t3);

        // c1 and c2 have the same instance of TestE so should have the same identity
        assert c1 == c2;
        // TestE does not implement Comparable so different instances should not be equal
        assert c1 != c3;
        assert c1 != c4;
    }

    void testConstWithNotOrderableProp() {
        TestConstWithNotComparableProp c1 = new TestConstWithNotComparableProp(new TestE(1));
        TestConstWithNotComparableProp c2 = new TestConstWithNotComparableProp(new TestE(1));
        TestConstWithNotComparableProp c3 = new TestConstWithNotComparableProp(new TestE(2));
        // TestE does not implement Orderable so will never be equal
        assert c1 <=> c2 != Equal;
        assert c1 <=> c3 != Equal;
    }

    void testConstWithNotHashableProp() {
        TestE t1 = new TestE(1);
        TestE t2 = new TestE(1);
        TestE t3 = new TestE(2);
        TestConstWithNotComparableProp c1 = new TestConstWithNotComparableProp(t1);
        TestConstWithNotComparableProp c2 = new TestConstWithNotComparableProp(t1);
        TestConstWithNotComparableProp c3 = new TestConstWithNotComparableProp(t2);
        TestConstWithNotComparableProp c4 = new TestConstWithNotComparableProp(t3);

        // Const.x doc states that a property of a const that is not Hashable should use zero for
        // its hash code. This means that all the consts will have the same hash code.
        assert c1.hashCode() == c2.hashCode();
        assert c1.hashCode() == c3.hashCode();
        assert c1.hashCode() == c4.hashCode();
    }

    void testConstWithServiceIsStringable() {
        TestServiceA         svc      = new TestServiceA();
        TestConstWithService c1       = new TestConstWithService(svc);
        String               expected = "(svc=" + svc.toString() + ")";
        assert c1.estimateStringLength() == expected.size;
        assert c1.toString() == expected;
    }

    void testConstWithNullablePropIsStringable() {
        TestConstWithNullableProp c1 = new TestConstWithNullableProp(Null);
        assert c1.estimateStringLength() == 8;
        assert c1.toString() == "(s=Null)";
        TestConstWithNullableProp c2 = new TestConstWithNullableProp("abcdef");
        assert c2.estimateStringLength() == 10;
        assert c2.toString() == "(s=abcdef)";
    }

    // ----- test TestConstAnts ------------------------------------------------------------------------

    const TestEmptyConst;

    const TestSimpleConst(Int n, String s);

    const TestConstWithConst(TestSimpleConst c);

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

    service TestServiceA
            implements Orderable, Hashable {

        static Int EqualsId = 1;
        static Int CompareId = 2;
        static Int HashCodeId = 3;

        static <CompileType extends TestServiceA> Boolean equals(CompileType value1, CompileType value2) {
            MethodTracker.called(EqualsId);
            return True;
        }

        static <CompileType extends TestServiceA> Ordered compare(CompileType value1, CompileType value2) {
            MethodTracker.called(CompareId);
            return Equal;
        }

        static <CompileType extends TestServiceA> Int hashCode(CompileType value1) {
            MethodTracker.called(HashCodeId);
            return 99;
        }
    }

    const TestConstWithNotComparableProp(TestE e);

    const TestConstWithService(TestServiceA svc);

    const TestConstA(Int n);

    const TestConstB(Int n, String s) extends TestConstA(n);

    const TestConstC(Int n, String s) extends TestConstB(n, s);

    const TestConstD(Int n, String s, Int i, String t) extends TestConstB(n, s);

    const TestConstWithBit(Bit b);
    const TestConstWithBoolean(Boolean b);
    const TestConstWithChar(Char c);
    const TestConstWithDec32(Dec32 d);
    const TestConstWithDec64(Dec64 d);
    const TestConstWithFloat32(Float32 f);
    const TestConstWithFloat64(Float64 f);
    const TestConstWithInt8(Int8 i);
    const TestConstWithInt16(Int16 i);
    const TestConstWithInt32(Int32 i);
    const TestConstWithInt64(Int64 i);
    const TestConstWithInt128(Int128 n);
    const TestConstWithNibble(Nibble n);
    const TestConstWithString(String s);
    const TestConstWithUInt8(UInt8 i);
    const TestConstWithUInt16(UInt16 i);
    const TestConstWithUInt32(UInt32 i);
    const TestConstWithUInt64(UInt64 i);
    const TestConstWithUInt128(UInt128 n);

    const TestConstWithNullableProp(String? s);

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