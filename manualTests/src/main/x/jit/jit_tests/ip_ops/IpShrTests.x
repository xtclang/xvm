/**
 * Tests for the JIT Op IP_Shr.java.
 */
class IpShrTests {

    @Inject Console console;

    // TODO: running this without the JIT fails - I think the non-JIT code is wrong
    void run() {
        console.print(">>>> Running IpShrTests >>>>");
        testIpShrInt();
        testIpShrUInt();
        testIpShrInt8();
        testIpShrUInt8();
        testIpShrInt16();
        testIpShrUInt16();
        testIpShrInt32();
        testIpShrUInt32();
        testIpShrInt128();
        testIpShrUInt128();
        console.print("<<<< Finished IpShrTests <<<<<");
    }

    void testIpShrInt() {
        Int value1 = 0x2AAAAAAAAAAAAAA0;
        value1 >>= 2;
        assert value1 == 0xAAAAAAAAAAAAAA8;
    }

    void testIpShrUInt() {
        UInt value1 = 0xAAAAAAAAAAAAAAAA;
        value1 >>= 2;
        assert value1 == 0x2AAAAAAAAAAAAAAA; // Fails in the non-JIT case with value1 == 16909515400900422314
    }

    void testIpShrInt8() {
        Int8 value1 = 0x4A;
        value1 >>= 2;
        assert value1 == 0x12;
    }

    void testIpShrUInt8() {
        UInt8 value1 = 0xAA;
        value1 >>= 2;
        assert value1 == 0x2A;
    }

    void testIpShrInt16() {
        Int16 value1 = 0x4A4A;
        value1 >>= 2;
        assert value1 == 0x1292;
    }

    void testIpShrUInt16() {
        UInt16 value1 = 0xAAAA;
        value1 >>= 2;
        assert value1 == 0x2AAA;
    }

    void testIpShrInt32() {
        Int32 value1 = 0x4A4A4A4A;
        value1 >>= 2;
        assert value1 == 0x12929292;
    }

    void testIpShrUInt32() {
        UInt32 value1 = 0xAAAAAAAA;
        value1 >>= 2;
        assert value1 == 0x2AAAAAAA;
    }

    void testIpShrInt128() {
//        Int128 value1 = 0x4A4A4A4A;
//        value1 >>= 2;
//        assert value1 == 0x12929292;
    }

    void testIpShrUInt128() {
//        UInt128 value1 = 0xAAAAAAAA;
//        value1 >>= 2;
//        assert value1 == 0x2AAAAAAA;
    }
}
