/**
 * Tests for the JIT Op IP_Xor.java.
 */
class IpXorTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running IpXorTests >>>>");
        testIpXorInt();
        testIpXorUInt();
        testIpXorInt8();
        testIpXorUInt8();
        testIpXorInt16();
        testIpXorUInt16();
        testIpXorInt32();
        testIpXorUInt32();
        testIpXorInt128();
        testIpXorUInt128();
        console.print("<<<< Finished IpXorTests <<<<<");
    }

    void testIpXorInt() {
        Int value1 = 0x0AAAAAAAAAAAAAAA;
        value1 ^= 0x00F0F0F0F0F0F0F0;
        assert value1 == 0x0A5A5A5A5A5A5A5A;
    }

    void testIpXorUInt() {
        UInt value1 = 0xAAAAAAAAAAAAAAAA;
        value1 ^= 0xF0F0F0F0F0F0F0F0;
        assert value1 == 0x5A5A5A5A5A5A5A5A;
    }

    void testIpXorInt8() {
        Int8 value1 = 0x4A;
        value1 ^= 0x0F;
        assert value1 == 0x45;
    }

    void testIpXorUInt8() {
        UInt8 value1 = 0xAA;
        value1 ^= 0xF0;
        assert value1 == 0x5A;
    }

    void testIpXorInt16() {
        Int16 value1 = 0x4A4A;
        value1 ^= 0x0FF0;
        assert value1 == 0x45BA;
    }

    void testIpXorUInt16() {
        UInt16 value1 = 0xAAAA;
        value1 ^= 0xF0F0;
        assert value1 == 0x5A5A;
    }

    void testIpXorInt32() {
        Int32 value1 = 0x4A4A4A4A;
        value1 ^= 0x0FF0F0F0;
        assert value1 == 0x45BABABA;
    }

    void testIpXorUInt32() {
        UInt32 value1 = 0xAAAAAAAA;
        value1 ^= 0xF0F0F0F0;
        assert value1 == 0x5A5A5A5A;
    }

    void testIpXorInt128() {
//        Int128 value1 = 0x4A4A4A4A;
//        value1 ^= 0x0FF0F0F0;
//        assert value1 == 0x45BABABA;
    }

    void testIpXorUInt128() {
//        UInt128 value1 = 0xAAAAAAAA;
//        value1 ^= 0xF0F0F0F0;
//        assert value1 == 0x5A5A5A5A;
    }
}
