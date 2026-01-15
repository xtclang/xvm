/**
 * Tests for the JIT Op GP_Shr.java.
 */
class GpShrTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running GpShrTests >>>>");
        testGpShrInt();
        testGpShrIntConstants();
        testGpShrUInt();
        testGpShrInt8();
        testGpShrUInt8();
        testGpShrInt16();
        testGpShrUInt16();
        testGpShrInt32();
        testGpShrUInt32();
        testGpShrInt128();
        testGpShrUInt128();
        console.print("<<<< Finished GpShrTests <<<<<");
    }

    void testGpShrInt() {
        Int value1 = 0;
        Int value2 = 0x2AAAAAAAAAAAAAA0;
        Int value3 = 2;
        value1 = value2 >> value3;
        assert value1 == 0xAAAAAAAAAAAAAA8;
    }

    void testGpShrIntConstants() {
        Int value = 0x2AAAAAAAAAAAAAA0 >> 2;
        assert value == 0xAAAAAAAAAAAAAA8;
    }

// TODO: Fails - Could not find an operation on "UInt" and "UInt" resulting in "UInt" type. (">>")
    void testGpShrUInt() {
//        UInt value1 = 0;
//        UInt value2 = 0xAAAAAAAAAAAAAAAA;
//        UInt value3 = 2;
//        value1 = value2 >> value3;
//        assert value1 == 0x2AAAAAAAAAAAAAAA;
    }

    void testGpShrInt8() {
        Int8 value1 = 0;
        Int8 value2 = 0x4A;
        Int  value3  = 2;
        value1 = value2 >> value3;
        assert value1 == 0x12;
    }

    void testGpShrUInt8() {
        UInt8 value1 = 0;
        UInt8 value2 = 0xAA;
        Int   value3 = 2;
        value1 = value2 >> value3;
        assert value1 == 0x2A;
    }

    void testGpShrInt16() {
        Int16 value1 = 0;
        Int16 value2 = 0x4A4A;
        Int   value3 = 2;
        value1 = value2 >> value3;
        assert value1 == 0x1292;
    }

    void testGpShrUInt16() {
        UInt16 value1 = 0;
        UInt16 value2 = 0xAAAA;
        Int    value3 = 2;
        value1 = value2 >> value3;
        assert value1 == 0x2AAA;
    }

    void testGpShrInt32() {
        Int32 value1 = 0;
        Int32 value2 = 0x4A4A4A4A;
        Int   value3 = 2;
        value1 = value2 >> value3;
        assert value1 == 0x12929292;
    }

    void testGpShrUInt32() {
        UInt32 value1 = 0;
        UInt32 value2 = 0xAAAAAAAA;
        Int    value3 = 2;
        value1 = value2 >> value3;
        assert value1 == 0x2AAAAAAA;
    }

    void testGpShrInt128() {
//        Int128 value1 = 0;
//        Int128 value2 = 0x4A4A4A4A;
//        Int    value3 = 2;
//        value1 = value2 >> value3;
//        assert value1 == 0x12929292;
    }

    void testGpShrUInt128() {
//        UInt128 value1 = 0;
//        UInt128 value2 = 0xAAAAAAAA;
//        Int     value3 = 2;
//        value1 = value2 >> value3;
//        assert value1 == 0x2AAAAAAA;
    }
}
