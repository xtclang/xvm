/**
 * Tests for the JIT Op GP_Sub.java.
 */
class GpSubTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running GpSubTests >>>>");
        testGpSubInt();
        testGpSubIntConstants();
        testGpSubUInt();
        testGpSubInt8();
        testGpSubUInt8();
        testGpSubInt16();
        testGpSubUInt16();
        testGpSubInt32();
        testGpSubUInt32();
        testGpSubInt128();
        testGpSubUInt128();
        testGpSubDec();
        testGpSubDec32();
        testGpSubIntFromChar();
        testGpSubCharFromChar();
        console.print("<<<< Finished GpSubTests <<<<");
    }

    void testGpSubInt() {
        Int value1 = 0;
        Int value2 = 76;
        Int value3 = 66;
        value1 = value2 - value3;
        assert value1 == 10;
    }

    void testGpSubIntConstants() {
        Int value = 76 - 66;
        assert value == 10;
    }

    void testGpSubUInt() {
        UInt value1 = 0;
        UInt value2 = 0xFFFFFFFFFFFFFFF1;
        UInt value3 = 1;
        value1 = value2 - value3;
        assert value1 == 0xFFFFFFFFFFFFFFF0;
    }

    void testGpSubInt8() {
        Int8 value1 = 0;
        Int8 value2 = 76;
        Int8 value3 = 66;
        value1 = value2 - value3;
        assert value1 == 10;
    }

    void testGpSubUInt8() {
        UInt8 value1 = 0;
        UInt8 value2 = 0xF1;
        UInt8 value3 = 1;
        value1 = value2 - value3;
        assert value1 == 0xF0;
    }

    void testGpSubInt16() {
        Int16 value1 = 0;
        Int16 value2 = 76;
        Int16 value3 = 66;
        value1 = value2 - value3;
        assert value1 == 10;
    }

    void testGpSubUInt16() {
        UInt16 value1 = 0;
        UInt16 value2 = 0xFFF1;
        UInt16 value3 = 1;
        value1 = value2 - value3;
        assert value1 == 0xFFF0;
    }

    void testGpSubInt32() {
        Int32 value1 = 0;
        Int32 value2 = 76;
        Int32 value3 = 66;
        value1 = value2 - value3;
        assert value1 == 10;
    }

    void testGpSubUInt32() {
        UInt32 value1 = 0;
        UInt32 value2 = 0xFFFFFFF1;
        UInt32 value3 = 1;
        value1 = value2 - value3;
        assert value1 == 0xFFFFFFF0;
    }

    void testGpSubInt128() {
// TODO: Need Int128 support
//        Int128 value1 = 0;
//        Int128 value2 = 76;
//        Int128 value3 = 66;
//        value1 = value2 - value3;
//        assert value1 == 10;
    }

    void testGpSubUInt128() {
// TODO: Need UInt128 support
//        UInt128 value1 = 0;
//        UInt128 value2 = 0xFFFFFFF1;
//        UInt128 value3 = 1;
//        value1 = value2 - value3;
//        assert value1 == 0xFFFFFFF0;
    }

    void testGpSubDec() {
// TODO: Need Dec support
//        Dec value1 = 0.0;
//        Dec value2 = 2.5;
//        Dec value3 = 1.25;
//        value1 = value2 - value3;
//        assert value1 == 3.75;
    }

    void testGpSubDec32() {
// TODO: Need Dec32 support
//        Dec32 value1 = 0.0;
//        Dec32 value2 = 3.75;
//        Dec32 value3 = 1.25;
//        value1 = value2 - value3;
//        assert value1 == 2.5;
    }

    void testGpSubIntFromChar() {
        Char value1 = ' ';
        Char value2 = 'd';
        Int  value3 = 3;
        value1 = value2 - value3;
        assert value1 == 'a';
    }

    void testGpSubCharFromChar() {
        UInt32 value1 = 0;
        Char   value2 = 'd';
        Char   value3 = 'a';
        value1 = value2 - value3;
        assert value1 == 3;
    }
}
