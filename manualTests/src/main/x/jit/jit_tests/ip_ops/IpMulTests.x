/**
 * Tests for the JIT Op IP_Mul.java.
 */
class IpMulTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running IpMulTests >>>>");
        testIpMulInt();
        testIpMulUInt();
        testIpMulInt8();
        testIpMulUInt8();
        testIpMulInt16();
        testIpMulUInt16();
        testIpMulInt32();
        testIpMulUInt32();
        testIpMulInt128();
        testIpMulUInt128();
        testIpMulDec();
        testIpMulDec32();
        console.print("<<<< Finished IpMulTests <<<<<");
    }

    void testIpMulInt() {
        Int value1 = 0x222222222222222;
        value1 *= 5;
        assert value1 == 0x0AAAAAAAAAAAAAAA;
    }

    void testIpMulUInt() {
        UInt value1 = 0x3222222222222222;
        value1 *= 5;
        assert value1 == 0xFAAAAAAAAAAAAAAA;
    }

    void testIpMulInt8() {
        Int8 value1 = 0x13;
        value1 *= 5;
        assert value1 == 0x5F;
    }

    void testIpMulUInt8() {
        UInt8 value1 = 0x33;
        value1 *= 5;
        assert value1 == 0xFF;
    }

    void testIpMulInt16() {
        Int16 value1 = 0x1333;
        value1 *= 5;
        assert value1 == 0x5FFF;
    }

    void testIpMulUInt16() {
        UInt16 value1 = 0x3333;
        value1 *= 5;
        assert value1 == 0xFFFF;
    }

    void testIpMulInt32() {
        Int32 value1 = 0x13333333;
        value1 *= 5;
        assert value1 == 0x5FFFFFFF;
    }

    void testIpMulUInt32() {
        UInt32 value1 = 0x33333333;
        value1 *= 5;
        assert value1 == 0xFFFFFFFF;
    }

    void testIpMulInt128() {
        Int128 value1 = 0x13333333;
        value1 *= 5;
        assert value1 == 0x5FFFFFFF;
    }

    void testIpMulUInt128() {
        UInt128 value1 = 0x33333333;
        value1 *= 5;
        assert value1 == 0xFFFFFFFF;
    }

    void testIpMulDec() {
//        Dec value1 = 4.25;
//        value1 *= 3.0;
//        Dec expected = 12.75;
//        assert value1 == expected; // Fails in non-JIT too
    }

    void testIpMulDec32() {
//        Dec32 value1 = 4.25;
//        value1 *= 3.0;
//        Dec expected = 12.75;
//        assert value1 == expected; // Fails in non-JIT too
    }
}
