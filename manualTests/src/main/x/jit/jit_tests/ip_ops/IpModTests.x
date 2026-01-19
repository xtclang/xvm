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
//        Int128 value1 = 0x5FFFFFF1;
//        value1 %= 5;
//        assert value1 == 1;
    }

    void testIpModUInt128() {
//        UInt128 value1 = 0xFFFFFFF1;
//        value1 %= 5;
//        assert value1 == 1;
    }
}
