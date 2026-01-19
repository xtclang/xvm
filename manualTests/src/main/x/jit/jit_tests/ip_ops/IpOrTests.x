/**
 * Tests for the JIT Op IP_Or.java.
 */
class IpOrTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running IpOrTests >>>>");
        testIpOrInt();
        testIpOrUInt();
        testIpOrInt8();
        testIpOrUInt8();
        testIpOrInt16();
        testIpOrUInt16();
        testIpOrInt32();
        testIpOrUInt32();
        testIpOrInt128();
        testIpOrUInt128();
        console.print("<<<< Finished IpOrTests <<<<<");
    }

    void testIpOrInt() {
        Int value1 = 0x0AAAAAAAAAAAAAAA;
        value1 |= 0x00F0F0F0F0F0F0F0;
        assert value1 == 0x0AFAFAFAFAFAFAFA;
    }

    void testIpOrUInt() {
        UInt value1 = 0xAAAAAAAAAAAAAAAA;
        value1 |= 0xF0F0F0F0F0F0F0F0;
        assert value1 == 0xFAFAFAFAFAFAFAFA;
    }

    void testIpOrInt8() {
        Int8 value1 = 0x4A;
        value1 |= 0x0F;
        assert value1 == 0x4F;
    }

    void testIpOrUInt8() {
        UInt8 value1 = 0xAA;
        value1 |= 0xF0;
        assert value1 == 0xFA;
    }

    void testIpOrInt16() {
        Int16 value1 = 0x4A4A;
        value1 |= 0x0FF0;
        assert value1 == 0x4FFA;
    }

    void testIpOrUInt16() {
        UInt16 value1 = 0xAAAA;
        value1 |= 0xF0F0;
        assert value1 == 0xFAFA;
    }

    void testIpOrInt32() {
        Int32 value1 = 0x4A4A4A4A;
        value1 |= 0x0FF0F0F0;
        assert value1 == 0x4FFAFAFA;
    }

    void testIpOrUInt32() {
        UInt32 value1 = 0xAAAAAAAA;
        value1 |= 0xF0F0F0F0;
        assert value1 == 0xFAFAFAFA;
    }

    void testIpOrInt128() {
//        Int128 value1 = 0x4A4A4A4A;
//        value1 |= 0x0FF0F0F0;
//        assert value1 == 0x4FFAFAFA;
    }

    void testIpOrUInt128() {
//        UInt128 value1 = 0xAAAAAAAA;
//        value1 |= 0xF0F0F0F0;
//        assert value1 == 0xFAFAFAFA;
    }
}
