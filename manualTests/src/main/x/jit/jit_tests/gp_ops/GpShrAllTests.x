/**
 * Tests for the JIT Op GP_ShrAll.java.
 */
class GpShrAllTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running GpShlAllTests >>>>");
        testGpShrAllInt();
        testGpShrAllIntConstants();
        testGpShrAllUInt();
        testGpShrAllInt8();
        testGpShrAllUInt8();
        testGpShrAllInt16();
        testGpShrAllUInt16();
        testGpShrAllInt32();
        testGpShrAllUInt32();
        testGpShrAllInt128();
        testGpShrAllUInt128();
        console.print("<<<< Finished GpShlAllTests <<<<<");
    }

    void testGpShrAllInt() {
        Int value1 = 0;
        Int value2 = 0x2AAAAAAAAAAAAAA0;
        Int value3 = 2;
        value1 = value2 >>> value3;
        assert value1 == 0xAAAAAAAAAAAAAA8;
    }

    void testGpShrAllIntConstants() {
        Int value = 0x2AAAAAAAAAAAAAA0 >>> 2;
        assert value == 0xAAAAAAAAAAAAAA8;
    }

// TODO: Fails - Could not find an operation on "UInt" and "UInt" resulting in "UInt" type. (">>>")
    void testGpShrAllUInt() {
//        UInt value1 = 0;
//        UInt value2 = 0xAAAAAAAAAAAAAAAA;
//        UInt value3 = 2;
//        value1 = value2 >>> value3;
//        assert value1 == 0x2AAAAAAAAAAAAAAA;
    }

    void testGpShrAllInt8() {
        Int8 value1 = 0;
        Int8 value2 = -1;
        Int value3  = 2;
        value1 = value2 >>> value3;
        assert value1 == 0x3F;
    }

    void testGpShrAllUInt8() {
        UInt8 value1 = 0;
        UInt8 value2 = 0xFF;
        Int   value3 = 2;
        value1 = value2 >>> value3;
        assert value1 == 0x3F;
    }

    void testGpShrAllInt16() {
        Int16 value1 = 0;
        Int16 value2 = 0x4A4A;
        Int   value3 = 2;
        value1 = value2 >>> value3;
        assert value1 == 0x1292;
    }

    void testGpShrAllUInt16() {
        UInt16 value1 = 0;
        UInt16 value2 = 0xAAAA;
        Int    value3 = 2;
        value1 = value2 >>> value3;
        assert value1 == 0x2AAA;
    }

    void testGpShrAllInt32() {
        Int32 value1 = 0;
        Int32 value2 = 0x4A4A4A4A;
        Int   value3 = 2;
        value1 = value2 >>> value3;
        assert value1 == 0x12929292;
    }

    void testGpShrAllUInt32() {
        UInt32 value1 = 0;
        UInt32 value2 = 0xAAAAAAAA;
        Int    value3 = 2;
        value1 = value2 >>> value3;
        assert value1 == 0x2AAAAAAA;
    }

    void testGpShrAllInt128() {
        Int128 value1 = 0;
        Int128 value2 = 0x4A4A4A4A;
        Int    value3 = 2;
        value1 = value2 >>> value3;
        assert value1 == 0x12929292;
    }

    void testGpShrAllUInt128() {
        UInt128 value1 = 0;
        UInt128 value2 = 0xAAAAAAAA;
        Int     value3 = 2;
        value1 = value2 >>> value3;
        assert value1 == 0x2AAAAAAA;
    }
}
