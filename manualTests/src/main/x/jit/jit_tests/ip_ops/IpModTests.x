/**
 * Tests for the JIT Op IP_Mod.java.
 */
class IpModTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running IpModTests >>>>");
        testIpModInt();
        testIpModUInt();
        testIpModInt8();
        testIpModUInt8();
        testIpModInt16();
        testIpModUInt16();
        testIpModInt32();
        testIpModUInt32();
        testIpModInt128();
        testIpModUInt128();
        testIpModDec32();
        testIpModDec64();
        testIpModDec128();
        testIpModFloat32();
        testIpModFloat64();
        console.print("<<<< Finished IpModTests <<<<<");
    }

    void testIpModInt() {
        Int value1 = 0x0AAAAAAAAAAAAAA1;
        value1 %= 5;
        assert value1 == 1;
    }

    void testIpModUInt() {
        UInt value1 = 0xFAAAAAAAAAAAAAA1;
        value1 %= 5;
        assert value1 == 1;
    }

    void testIpModInt8() {
        Int8 value1 = 0x51;
        value1 %= 5;
        assert value1 == 1;
    }

    void testIpModUInt8() {
        UInt8 value1 = 0xF1;
        value1 %= 5;
        assert value1 == 1;
    }

    void testIpModInt16() {
        Int16 value1 = 0x5FF1;
        value1 %= 5;
        assert value1 == 1;
    }

    void testIpModUInt16() {
        UInt16 value1 = 0xFFF1;
        value1 %= 5;
        assert value1 == 1;
    }

    void testIpModInt32() {
        Int32 value1 = 0x5FFFFFF1;
        value1 %= 5;
        assert value1 == 1;
    }

    void testIpModUInt32() {
        UInt32 value1 = 0xFFFFFFF1;
        value1 %= 5;
        assert value1 == 1;
    }

    void testIpModInt128() {
        Int128 value1 = 0x5FFFFFF1;
        value1 %= 5;
        assert value1 == 1;
    }

    void testIpModUInt128() {
        UInt128 value1 = 0xFFFFFFF1;
        value1 %= 5;
        assert value1 == 1;
    }

    void testIpModDec32() {
        Dec32 value1 = 10.5;
        value1 %= 5;
        assert value1 == 0.5;
    }

    void testIpModDec64() {
        Dec64 value1 = 10.5;
        value1 %= 5;
        assert value1 == 0.5;
    }

    void testIpModDec128() {
        Dec128 value1 = 10.5;
        value1 %= 5;
        assert value1 == 0.5;
    }

    void testIpModFloat32() {
        Float32 value1 = 10.5;
        value1 %= 5;
        assert value1 == 0.5;
    }

    void testIpModFloat64() {
        Float64 value1 = 10.5;
        value1 %= 5;
        assert value1 == 0.5;
    }
}
