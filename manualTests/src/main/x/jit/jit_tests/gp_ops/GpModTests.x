/**
 * Tests for the JIT Op GP_Mod.java.
 */
class GpModTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running GpModTests >>>>");
        testGpModInt();
        testGpModIntConstants();
        testGpModUInt();
        testGpModInt8();
        testGpModUInt8();
        testGpModInt16();
        testGpModUInt16();
        testGpModInt32();
        testGpModUInt32();
        testGpModInt128();
        testGpModUInt128();
        console.print("<<<< Finished GpModTests <<<<<");
    }

    void testGpModInt() {
        Int value1 = 0;
        Int value2 = 0x0AAAAAAAAAAAAAA1;
        Int value3 = 5;
        value1 = value2 % value3;
        assert value1 == 1;
    }

    void testGpModIntConstants() {
        Int value = 0x0AAAAAAAAAAAAAA1 % 5;
        assert value == 1;
    }

    void testGpModUInt() {
        UInt value1 = 0;
        UInt value2 = 0xFAAAAAAAAAAAAAA1;
        UInt value3 = 5;
        value1 = value2 % value3;
        assert value1 == 1; // fails in the JIT with value1 == 0
    }

    void testGpModInt8() {
        Int8 value1 = 0;
        Int8 value2 = 0x51;
        Int8 value3 = 5;
        value1 = value2 % value3;
        assert value1 == 1;
    }

    void testGpModUInt8() {
        UInt8 value1 = 0;
        UInt8 value2 = 0xF1;
        UInt8 value3 = 5;
        value1 = value2 % value3;
        assert value1 == 1;
    }

    void testGpModInt16() {
        Int16 value1 = 0;
        Int16 value2 = 0x5FF1;
        Int16 value3 = 5;
        value1 = value2 % value3;
        assert value1 == 1;
    }

    void testGpModUInt16() {
        UInt16 value1 = 0;
        UInt16 value2 = 0xFFF1;
        UInt16 value3 = 5;
        value1 = value2 % value3;
        assert value1 == 1;
    }

    void testGpModInt32() {
        Int32 value1 = 0;
        Int32 value2 = 0x5FFFFFF1;
        Int32 value3 = 5;
        value1 = value2 % value3;
        assert value1 == 1;
    }

    void testGpModUInt32() {
        UInt32 value1 = 0;
        UInt32 value2 = 0xFFFFFFF1;
        UInt32 value3 = 5;
        value1 = value2 % value3;
//        assert value1 == 1;
    }

    void testGpModInt128() {
//        Int128 value1 = 0;
//        Int128 value2 = 0x5FFFFFF1;
//        Int128 value3 = 5;
//        value1 = value2 % value3;
//        assert value1 == 1;
    }

    void testGpModUInt128() {
//        UInt128 value1 = 0;
//        UInt128 value2 = 0xFFFFFFF1;
//        UInt128 value3 = 5;
//        value1 = value2 % value3;
//        assert value1 == 1;
    }
}
