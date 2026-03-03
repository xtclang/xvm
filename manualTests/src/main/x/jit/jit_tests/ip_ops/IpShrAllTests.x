/**
 * Tests for the JIT Op IP_ShrAll.java.
 */
class IpShrAllTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running IpShrAllTests >>>>");
        testIpShrAllInt();
        testIpShrAllUInt();
        testIpShrAllInt8();
        testIpShrAllUInt8();
        testIpShrAllInt16();
        testIpShrAllUInt16();
        testIpShrAllInt32();
        testIpShrAllUInt32();
        testIpShrAllInt128();
        testIpShrAllUInt128();
        console.print("<<<< Finished IpShrAllTests <<<<<");
    }

    void testIpShrAllInt() {
        Int value1 = 0x2AAAAAAAAAAAAAA0;
        value1 >>>= 2;
        assert value1 == 0xAAAAAAAAAAAAAA8;
    }

    void testIpShrAllUInt() {
        UInt value1 = 0xAAAAAAAAAAAAAAAA;
        value1 >>>= 2;
        assert value1 == 0x2AAAAAAAAAAAAAAA;
    }

    void testIpShrAllInt8() {
        Int8 value1 = -1;
        value1 >>>= 2;
        assert value1 == 0x3F; // Fails in the non-JIT case with value1 == -1
    }

    void testIpShrAllUInt8() {
        UInt8 value1 = 0xFF;
        value1 >>>= 2;
        assert value1 == 0x3F;
    }

    void testIpShrAllInt16() {
        Int16 value1 = 0x4A4A;
        value1 >>>= 2;
        assert value1 == 0x1292;
    }

    void testIpShrAllUInt16() {
        UInt16 value1 = 0xAAAA;
        value1 >>>= 2;
        assert value1 == 0x2AAA;
    }

    void testIpShrAllInt32() {
        Int32 value1 = 0x4A4A4A4A;
        value1 >>>= 2;
        assert value1 == 0x12929292;
    }

    void testIpShrAllUInt32() {
        UInt32 value1 = 0xAAAAAAAA;
        value1 >>>= 2;
        assert value1 == 0x2AAAAAAA;
    }

    void testIpShrAllInt128() {
        Int128 value1 = 0x4A4A4A4A;
        value1 >>>= 2;
        assert value1 == 0x12929292;
    }

    void testIpShrAllUInt128() {
        UInt128 value1 = 0xAAAAAAAA;
        value1 >>>= 2;
        assert value1 == 0x2AAAAAAA;
    }
}
