/**
 * Tests for the JIT Op IP_And.java.
 */
class IpAndTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running IpAndTests >>>>");
        testIpAndInt();
        testIpAndUInt();
        testIpAndInt8();
        testIpAndUInt8();
        testIpAndInt16();
        testIpAndUInt16();
        testIpAndInt32();
        testIpAndUInt32();
        testIpAndInt128();
        testIpAndUInt128();
        console.print("<<<< Finished IpAndTests <<<<<");
    }

    void testIpAndInt() {
        Int value1 = 0x0AAAAAAAAAAAAAAA;
        value1 &= 0x00F0F0F0F0F0F0F0;
        assert value1 == 0x00A0A0A0A0A0A0A0;
    }

    void testIpAndUInt() {
        UInt value1 = 0xAAAAAAAAAAAAAAAA;
        value1 &= 0xF0F0F0F0F0F0F0F0;
        assert value1 == 0xA0A0A0A0A0A0A0A0;
    }

    void testIpAndInt8() {
        Int8 value1 = 0x4A;
        value1 &= 0x0F;
        assert value1 == 0x0A;
    }

    void testIpAndUInt8() {
        UInt8 value1 = 0xAA;
        value1 &= 0xF0;
        assert value1 == 0xA0;
    }

    void testIpAndInt16() {
        Int16 value1 = 0x4A4A;
        value1 &= 0x0FF0;
        assert value1 == 0x0A40;
    }

    void testIpAndUInt16() {
        UInt16 value1 = 0xAAAA;
        value1 &= 0xF0F0;
        assert value1 == 0xA0A0;
    }

    void testIpAndInt32() {
        Int32 value1 = 0x4A4A4A4A;
        value1 &= 0x0FF0F0F0;
        assert value1 == 0x0A404040;
    }

    void testIpAndUInt32() {
        UInt32 value1 = 0xAAAAAAAA;
        value1 &= 0xF0F0F0F0;
        assert value1 == 0xA0A0A0A0;
    }

    void testIpAndInt128() {
        Int128 value1 = 0x4A4A4A4A;
        value1 &= 0x0FF0F0F0;
        assert value1 == 0x0A404040;
    }

    void testIpAndUInt128() {
        UInt128 value1 = 0xAAAAAAAA;
        value1 &= 0xF0F0F0F0;
        assert value1 == 0xA0A0A0A0;
    }
}
