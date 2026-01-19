/**
 * Tests for the JIT Op GP_And.java.
 */
class GpAndTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running GpAndTests >>>>");
        testGpAndInt();
        testGpAndIntConstants();
        testGpAndUInt();
        testGpAndInt8();
        testGpAndUInt8();
        testGpAndInt16();
        testGpAndUInt16();
        testGpAndInt32();
        testGpAndUInt32();
        testGpAndInt128();
        testGpAndUInt128();
        console.print("<<<< Finished GpAndTests <<<<<");
    }

    void testGpAndInt() {
        Int value1 = 0;
        Int value2 = 0x0AAAAAAAAAAAAAAA;
        Int value3 = 0x00F0F0F0F0F0F0F0;
        value1 = value2 & value3;
        assert value1 == 0x00A0A0A0A0A0A0A0;
    }

    void testGpAndIntConstants() {
        Int value = 0x0AAAAAAAAAAAAAAA & 0x00F0F0F0F0F0F0F0;
        assert value == 0x00A0A0A0A0A0A0A0;
    }

    void testGpAndUInt() {
        UInt value1 = 0;
        UInt value2 = 0xAAAAAAAAAAAAAAAA;
        UInt value3 = 0xF0F0F0F0F0F0F0F0;
        value1 = value2 & value3;
        assert value1 == 0xA0A0A0A0A0A0A0A0;
    }

    void testGpAndInt8() {
        Int8 value1 = 0;
        Int8 value2 = 0x4A;
        Int8 value3 = 0x0F;
        value1 = value2 & value3;
        assert value1 == 0x0A;
    }

    void testGpAndUInt8() {
        UInt8 value1 = 0;
        UInt8 value2 = 0xAA;
        UInt8 value3 = 0xF0;
        value1 = value2 & value3;
        assert value1 == 0xA0;
    }

    void testGpAndInt16() {
        Int16 value1 = 0;
        Int16 value2 = 0x4A4A;
        Int16 value3 = 0x0FF0;
        value1 = value2 & value3;
        assert value1 == 0x0A40;
    }

    void testGpAndUInt16() {
        UInt16 value1 = 0;
        UInt16 value2 = 0xAAAA;
        UInt16 value3 = 0xF0F0;
        value1 = value2 & value3;
        assert value1 == 0xA0A0;
    }

    void testGpAndInt32() {
        Int32 value1 = 0;
        Int32 value2 = 0x4A4A4A4A;
        Int32 value3 = 0x0FF0F0F0;
        value1 = value2 & value3;
        assert value1 == 0x0A404040;
    }

    void testGpAndUInt32() {
        UInt32 value1 = 0;
        UInt32 value2 = 0xAAAAAAAA;
        UInt32 value3 = 0xF0F0F0F0;
        value1 = value2 & value3;
        assert value1 == 0xA0A0A0A0;
    }

    void testGpAndInt128() {
//        Int128 value1 = 0;
//        Int128 value2 = 0x4A4A4A4A;
//        Int128 value3 = 0x0FF0F0F0;
//        value1 = value2 & value3;
//        assert value1 == 0x0A404040;
    }

    void testGpAndUInt128() {
//        UInt128 value1 = 0;
//        UInt128 value2 = 0xAAAAAAAA;
//        UInt128 value3 = 0xF0F0F0F0;
//        value1 = value2 & value3;
//        assert value1 == 0xA0A0A0A0;
    }
}
