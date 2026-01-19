/**
 * Tests for the JIT Op GP_Mul.java.
 */
class GpMulTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running GpMulTests >>>>");
        testGpMulInt();
        testGpMulIntConstants();
        testGpMulUInt();
        testGpMulInt8();
        testGpMulUInt8();
        testGpMulInt16();
        testGpMulUInt16();
        testGpMulInt32();
        testGpMulUInt32();
        testGpMulInt128();
        testGpMulUInt128();
        testGpMulDec();
        testGpMulDec32();
        console.print("<<<< Finished GpMulTests <<<<<");
    }

    void testGpMulInt() {
        Int value1 = 0;
        Int value2 = 0x222222222222222;
        Int value3 = 5;
        value1 = value2 * value3;
        assert value1 == 0x0AAAAAAAAAAAAAAA;
    }

    void testGpMulIntConstants() {
        Int value = 0x222222222222222 * 5;
        assert value == 0x0AAAAAAAAAAAAAAA;
    }

    void testGpMulUInt() {
        UInt value1 = 0;
        UInt value2 = 0x3222222222222222;
        UInt value3 = 5;
        value1 = value2 * value3;
        assert value1 == 0xFAAAAAAAAAAAAAAA;
    }

    void testGpMulInt8() {
        Int8 value1 = 0;
        Int8 value2 = 0x13;
        Int8 value3 = 5;
        value1 = value2 * value3;
        assert value1 == 0x5F;
    }

    void testGpMulUInt8() {
        UInt8 value1 = 0;
        UInt8 value2 = 0x33;
        UInt8 value3 = 5;
        value1 = value2 * value3;
        assert value1 == 0xFF;
    }

    void testGpMulInt16() {
        Int16 value1 = 0;
        Int16 value2 = 0x1333;
        Int16 value3 = 5;
        value1 = value2 * value3;
        assert value1 == 0x5FFF;
    }

    void testGpMulUInt16() {
        UInt16 value1 = 0;
        UInt16 value2 = 0x3333;
        UInt16 value3 = 5;
        value1 = value2 * value3;
        assert value1 == 0xFFFF;
    }

    void testGpMulInt32() {
        Int32 value1 = 0;
        Int32 value2 = 0x13333333;
        Int32 value3 = 5;
        value1 = value2 * value3;
        assert value1 == 0x5FFFFFFF;
    }

    void testGpMulUInt32() {
        UInt32 value1 = 0;
        UInt32 value2 = 0x33333333;
        UInt32 value3 = 5;
        value1 = value2 * value3;
        assert value1 == 0xFFFFFFFF;
    }

    void testGpMulInt128() {
//        Int128 value1 = 0;
//        Int128 value2 = 0x13333333;
//        Int128 value3 = 5;
//        value1 = value2 * value3;
//        assert value1 == 0x5FFFFFFF;
    }

    void testGpMulUInt128() {
//        UInt128 value1 = 0;
//        UInt128 value2 = 0x33333333;
//        UInt128 value3 = 5;
//        value1 = value2 * value3;
//        assert value1 == 0xFFFFFFFF;
    }

    void testGpMulDec() {
//        Dec value1 = 0;
//        Dec value2 = 4.25;
//        Dec value3 = 3.0;
//        value1 = value2 * value3;
//        Dec expected = 12.75;
//        assert value1 == expected; // Fails in non-JIT too
    }

    void testGpMulDec32() {
//        Dec32 value1 = 0;
//        Dec32 value2 = 4.25;
//        Dec32 value3 = 3.0;
//        value1 = value2 * value3;
//        Dec expected = 12.75;
//        assert value1 == expected; // Fails in non-JIT too
    }
}
