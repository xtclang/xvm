/**
 * Tests for the JIT Op GP_Divrem.java.
 */
class GpDivremTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running GpDivrem Tests >>>>");
        testGpDivremInt();
        testGpDivremUInt();
        testGpDivremInt8();
        testGpDivremUInt8();
        testGpDivremInt16();
        testGpDivremUInt16();
        testGpDivremInt32();
        testGpDivremUInt32();
        testGpDivremInt128();
        testGpDivremUInt128();
        testGpDivremIntN();
        testGpDivremUIntN();
        testGpDivremDec();
        testGpDivremDec32();
        testGpDivremDec128();
        testGpDivremFloat32();
        testGpDivremFloat64();
        console.print("<<<< Finished GpDivrem Tests <<<<<");
    }

    void testGpDivremInt() {
        Int value1 = 100;
        Int value2 = 3;
        (Int quotient, Int remainder) = value1 /% value2;
        assert quotient == 33;
        assert remainder == 1;
    }

    void testGpDivremUInt() {
        UInt value1 = 100;
        UInt value2 = 3;
        (UInt quotient, UInt remainder) = value1 /% value2;
        assert quotient == 33;
        assert remainder == 1;
    }

    void testGpDivremInt8() {
        Int8 value1 = 100;
        Int8 value2 = 3;
        (Int8 quotient, Int8 remainder) = value1 /% value2;
        assert quotient == 33;
        assert remainder == 1;
    }

    void testGpDivremUInt8() {
        UInt8 value1 = 100;
        UInt8 value2 = 3;
        (UInt8 quotient, UInt8 remainder) = value1 /% value2;
        assert quotient == 33;
        assert remainder == 1;
    }

    void testGpDivremInt16() {
        Int16 value1 = 100;
        Int16 value2 = 3;
        (Int16 quotient, Int16 remainder) = value1 /% value2;
        assert quotient == 33;
        assert remainder == 1;
    }

    void testGpDivremUInt16() {
        UInt16 value1 = 100;
        UInt16 value2 = 3;
        (UInt16 quotient, UInt16 remainder) = value1 /% value2;
        assert quotient == 33;
        assert remainder == 1;
    }

    void testGpDivremInt32() {
        Int32 value1 = 100;
        Int32 value2 = 3;
        (Int32 quotient, Int32 remainder) = value1 /% value2;
        assert quotient == 33;
        assert remainder == 1;
    }

    void testGpDivremUInt32() {
        UInt32 value1 = 100;
        UInt32 value2 = 3;
        (UInt32 quotient, UInt32 remainder) = value1 /% value2;
        assert quotient == 33;
        assert remainder == 1;
    }

    void testGpDivremInt128() {
        Int128 value1 = 100;
        Int128 value2 = 3;
        (Int128 quotient, Int128 remainder) = value1 /% value2;
        assert quotient == 33;
        assert remainder == 1;
    }

    void testGpDivremUInt128() {
        UInt128 value1 = 100;
        UInt128 value2 = 3;
        (UInt128 quotient, UInt128 remainder) = value1 /% value2;
        assert quotient == 33;
        assert remainder == 1;
    }

    void testGpDivremIntN() {
        IntN value1 = 100;
        IntN value2 = 3;
        (IntN quotient, IntN remainder) = value1 /% value2;
        assert quotient == 33;
        assert remainder == 1;
    }

    void testGpDivremUIntN() {
        UIntN value1 = 100;
        UIntN value2 = 3;
        (UIntN quotient, UIntN remainder) = value1 /% value2;
        assert quotient == 33;
        assert remainder == 1;
    }

    void testGpDivremDec() {
        Dec value1 = 100;
        Dec value2 = 3;
        (Dec quotient, Dec remainder) = value1 /% value2;
        assert quotient == 33;
        assert remainder == 1;
    }

    void testGpDivremDec32() {
        Dec32 value1 = 100;
        Dec32 value2 = 3;
        (Dec32 quotient, Dec32 remainder) = value1 /% value2;
        assert quotient == 33;
        assert remainder == 1;
    }

    void testGpDivremDec128() {
        Dec128 value1 = 100;
        Dec128 value2 = 3;
        (Dec128 quotient, Dec128 remainder) = value1 /% value2;
        assert quotient == 33;
        assert remainder == 1;
    }

    void testGpDivremFloat32() {
        Float32 value1 = 100;
        Float32 value2 = 3;
        (Float32 quotient, Float32 remainder) = value1 /% value2;
        assert quotient == 33.333332;
        assert remainder == 0;
    }

    void testGpDivremFloat64() {
        Float64 value1 = 100;
        Float64 value2 = 3;
        (Float64 quotient, Float64 remainder) = value1 /% value2;
        assert quotient == 33.333333333333336;
        assert remainder == 0;
    }
}
