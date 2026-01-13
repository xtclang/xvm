/**
 * Tests for the JIT Op GP_Or.java.
 */
class GpOrTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running GpOrTests >>>>");
        testGpOrInt();
        testGpOrIntConstants();
        testGpOrUInt();
        testGpOrInt8();
        testGpOrUInt8();
        testGpOrInt16();
        testGpOrUInt16();
        testGpOrInt32();
        testGpOrUInt32();
        testGpOrInt128();
        testGpOrUInt128();
        console.print("<<<< Finished GpOrTests <<<<<");
    }

    void testGpOrInt() {
        Int value1 = 0;
        Int value2 = 0x0AAAAAAAAAAAAAAA;
        Int value3 = 0x00F0F0F0F0F0F0F0;
        value1 = value2 | value3;
        assert value1 == 0x0AFAFAFAFAFAFAFA;
    }

    void testGpOrIntConstants() {
        Int value = 0x0AAAAAAAAAAAAAAA | 0x00F0F0F0F0F0F0F0;
        assert value == 0x0AFAFAFAFAFAFAFA;
    }

    void testGpOrUInt() {
        UInt value1 = 0;
        UInt value2 = 0xAAAAAAAAAAAAAAAA;
        UInt value3 = 0xF0F0F0F0F0F0F0F0;
        value1 = value2 | value3;
        assert value1 == 0xFAFAFAFAFAFAFAFA;
    }

    void testGpOrInt8() {
        Int8 value1 = 0;
        Int8 value2 = 0x4A;
        Int8 value3 = 0x0F;
        value1 = value2 | value3;
        assert value1 == 0x4F;
    }

    void testGpOrUInt8() {
        UInt8 value1 = 0;
        UInt8 value2 = 0xAA;
        UInt8 value3 = 0xF0;
        value1 = value2 | value3;
        assert value1 == 0xFA;
    }

    void testGpOrInt16() {
        Int16 value1 = 0;
        Int16 value2 = 0x4A4A;
        Int16 value3 = 0x0FF0;
        value1 = value2 | value3;
        assert value1 == 0x4FFA;
    }

    void testGpOrUInt16() {
        UInt16 value1 = 0;
        UInt16 value2 = 0xAAAA;
        UInt16 value3 = 0xF0F0;
        value1 = value2 | value3;
        assert value1 == 0xFAFA;
    }

    void testGpOrInt32() {
        Int32 value1 = 0;
        Int32 value2 = 0x4A4A4A4A;
        Int32 value3 = 0x0FF0F0F0;
        value1 = value2 | value3;
        assert value1 == 0x4FFAFAFA;
    }

    void testGpOrUInt32() {
        UInt32 value1 = 0;
        UInt32 value2 = 0xAAAAAAAA;
        UInt32 value3 = 0xF0F0F0F0;
        value1 = value2 | value3;
        assert value1 == 0xFAFAFAFA;
    }

    void testGpOrInt128() {
//        Int128 value1 = 0;
//        Int128 value2 = 0x4A4A4A4A;
//        Int128 value3 = 0x0FF0F0F0;
//        value1 = value2 | value3;
//        assert value1 == 0x4FFAFAFA;
    }

    void testGpOrUInt128() {
//        UInt128 value1 = 0;
//        UInt128 value2 = 0xAAAAAAAA;
//        UInt128 value3 = 0xF0F0F0F0;
//        value1 = value2 | value3;
//        assert value1 == 0xFAFAFAFA;
    }
}
