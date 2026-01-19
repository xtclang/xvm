/**
 * Tests for the JIT Op GP_Shl.java.
 */
class GpShlTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running GpShlTests >>>>");
        testGpShlInt();
        testGpShlIntConstants();
        testGpShlUInt();
        testGpShlInt8();
        testGpShlUInt8();
        testGpShlInt16();
        testGpShlUInt16();
        testGpShlInt32();
        testGpShlUInt32();
        testGpShlInt128();
        testGpShlUInt128();
        console.print("<<<< Finished GpShlTests <<<<<");
    }

    void testGpShlInt() {
        Int value1 = 0;
        Int value2 = 0x4AAAAAAAAAAAAAA8;
        Int value3 = 2;
        value1 = value2 << value3;
        assert value1 == 0x2AAAAAAAAAAAAAA0;
    }

    void testGpShlIntConstants() {
        Int value = 0x4AAAAAAAAAAAAAA8 << 2;
        assert value == 0x2AAAAAAAAAAAAAA0;
    }

// TODO: Fails - Could not find an operation on "UInt" and "UInt" resulting in "UInt" type. ("<<")
    void testGpShlUInt() {
//        UInt value1 = 0;
//        UInt value2 = 0xAAAAAAAAAAAAAAAA;
//        UInt value3 = 2;
//        value1 = value2 << value3;
//        assert value1 == 0xAAAAAAAAAAAAAAA8;
    }

    void testGpShlInt8() {
        Int8 value1 = 0;
        Int8 value2 = 0x4A;
        Int value3  = 2;
        value1 = value2 << value3;
        assert value1 == 0x28;
    }

    void testGpShlUInt8() {
        UInt8 value1 = 0;
        UInt8 value2 = 0xAA;
        Int   value3 = 2;
        value1 = value2 << value3;
        assert value1 == 0xA8;
    }

    void testGpShlInt16() {
        Int16 value1 = 0;
        Int16 value2 = 0x4A4A;
        Int   value3 = 2;
        value1 = value2 << value3;
        assert value1 == 0x2928;
    }

    void testGpShlUInt16() {
        UInt16 value1 = 0;
        UInt16 value2 = 0xAAAA;
        Int    value3 = 2;
        value1 = value2 << value3;
        assert value1 == 0xAAA8;
    }

    void testGpShlInt32() {
        Int32 value1 = 0;
        Int32 value2 = 0x4A4A4A4A;
        Int   value3 = 2;
        value1 = value2 << value3;
        assert value1 == 0x29292928;
    }

    void testGpShlUInt32() {
        UInt32 value1 = 0;
        UInt32 value2 = 0xAAAAAAAA;
        Int    value3 = 2;
        value1 = value2 << value3;
        assert value1 == 0xAAAAAAA8;
    }

    void testGpShlInt128() {
//        Int128 value1 = 0;
//        Int128 value2 = 0x4A4A4A4A;
//        Int    value3 = 2;
//        value1 = value2 << value3;
//        assert value1 == 0x29292928;
    }

    void testGpShlUInt128() {
//        UInt128 value1 = 0;
//        UInt128 value2 = 0xAAAAAAAA;
//        Int     value3 = 2;
//        value1 = value2 << value3;
//        assert value1 == 0xAAAAAAA8;
    }
}
