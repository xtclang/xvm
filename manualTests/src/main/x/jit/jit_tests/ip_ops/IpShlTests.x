/**
 * Tests for the JIT Op IP_Shl.java.
 */
class IpShlTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running IpShlTests >>>>");
        testIpShlInt();
        testIpShlUInt();
        testIpShlInt8();
        testIpShlUInt8();
        testIpShlInt16();
        testIpShlUInt16();
        testIpShlInt32();
        testIpShlUInt32();
        testIpShlInt128();
        testIpShlUInt128();
        console.print("<<<< Finished IpShlTests <<<<<");
    }

    void testIpShlInt() {
        Int value1 = 0x0AAAAAAAAAAAAAAA;
        value1 <<= 2;
        assert value1 == 0x2AAAAAAAAAAAAAA8;
    }

    void testIpShlUInt() {
        UInt value1 = 0xAAAAAAAAAAAAAAAA;
        value1 <<= 2;
        assert value1 == 0xAAAAAAAAAAAAAAA8;
    }

    void testIpShlInt8() {
        Int8 value1 = 0x4A;
        value1 <<= 2;
        assert value1 == 0x28;
    }

    void testIpShlUInt8() {
        UInt8 value1 = 0xAA;
        value1 <<= 2;
        assert value1 == 0xA8;
    }

    void testIpShlInt16() {
        Int16 value1 = 0x4A4A;
        value1 <<= 2;
        assert value1 == 0x2928;
    }

    void testIpShlUInt16() {
        UInt16 value1 = 0xAAAA;
        value1 <<= 2;
        assert value1 == 0xAAA8;
    }

    void testIpShlInt32() {
        Int32 value1 = 0x4A4A4A4A;
        value1 <<= 2;
        assert value1 == 0x29292928;
    }

    void testIpShlUInt32() {
        UInt32 value1 = 0xAAAAAAAA;
        value1 <<= 2;
        assert value1 == 0xAAAAAAA8;
    }

    void testIpShlInt128() {
//        Int128 value1 = 0x4A4A4A4A;
//        value1 <<= 2;
//        assert value1 == 0x29292928;
    }

    void testIpShlUInt128() {
//        UInt128 value1 = 0xAAAAAAAA;
//        value1 <<= 2;
//        assert value1 == 0xAAAAAAA8;
    }
}
