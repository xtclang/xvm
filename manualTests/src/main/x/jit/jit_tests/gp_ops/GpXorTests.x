/**
 * Tests for the JIT Op GP_Xor.java.
 */
class GpXorTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running GpXorTests >>>>");
        testGpXorInt();
        testGpXorUInt();
        testGpXorInt8();
        testGpXorUInt8();
        testGpXorInt16();
        testGpXorUInt16();
        testGpXorInt32();
        testGpXorUInt32();
        testGpXorInt128();
        testGpXorUInt128();
        console.print("<<<< Finished GpXorTests <<<<<");
    }

    void testGpXorInt() {
        Int value1 = 0;
        Int value2 = 0x0AAAAAAAAAAAAAAA;
        Int value3 = 0x00F0F0F0F0F0F0F0;
        value1 = value2 ^ value3;
        assert value1 == 0x0A5A5A5A5A5A5A5A;
    }

    void testGpXorIntConstants() {
        Int value = 0x0AAAAAAAAAAAAAAA ^ 0x00F0F0F0F0F0F0F0;
        assert value == 0x0A5A5A5A5A5A5A5A;
    }

    void testGpXorUInt() {
        UInt value1 = 0;
        UInt value2 = 0xAAAAAAAAAAAAAAAA;
        UInt value3 = 0xF0F0F0F0F0F0F0F0;
        value1 = value2 ^ value3;
        assert value1 == 0x5A5A5A5A5A5A5A5A;
    }

    void testGpXorInt8() {
        Int8 value1 = 0;
        Int8 value2 = 0x4A;
        Int8 value3 = 0x0F;
        value1 = value2 ^ value3;
        assert value1 == 0x45;
    }

    void testGpXorUInt8() {
        UInt8 value1 = 0;
        UInt8 value2 = 0xAA;
        UInt8 value3 = 0xF0;
        value1 = value2 ^ value3;
        assert value1 == 0x5A;
    }

    void testGpXorInt16() {
        Int16 value1 = 0;
        Int16 value2 = 0x4A4A;
        Int16 value3 = 0x0FF0;
        value1 = value2 ^ value3;
        assert value1 == 0x45BA;
    }

    void testGpXorUInt16() {
        UInt16 value1 = 0;
        UInt16 value2 = 0xAAAA;
        UInt16 value3 = 0xF0F0;
        value1 = value2 ^ value3;
        assert value1 == 0x5A5A;
    }

    void testGpXorInt32() {
        Int32 value1 = 0;
        Int32 value2 = 0x4A4A4A4A;
        Int32 value3 = 0x0FF0F0F0;
        value1 = value2 ^ value3;
        assert value1 == 0x45BABABA;
    }

    void testGpXorUInt32() {
        UInt32 value1 = 0;
        UInt32 value2 = 0xAAAAAAAA;
        UInt32 value3 = 0xF0F0F0F0;
        value1 = value2 ^ value3;
        assert value1 == 0x5A5A5A5A;
    }

    void testGpXorInt128() {
//        Int128 value1 = 0;
//        Int128 value2 = 0x4A4A4A4A;
//        Int128 value3 = 0x0FF0F0F0;
//        value1 = value2 ^ value3;
//        assert value1 == 0x45BABABA;
    }

    void testGpXorUInt128() {
//        UInt128 value1 = 0;
//        UInt128 value2 = 0xAAAAAAAA;
//        UInt128 value3 = 0xF0F0F0F0;
//        value1 = value2 ^ value3;
//        assert value1 == 0x5A5A5A5A;
    }
}
