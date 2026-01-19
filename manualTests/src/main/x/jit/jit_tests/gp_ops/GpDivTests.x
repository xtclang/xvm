/**
 * Tests for the JIT Op GP_Div.java.
 */
class GpDivTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running GpDivTests >>>>");
        testGpDivInt();
        testGpDivIntConstants();
        testGpDivUInt();
        testGpDivInt8();
        testGpDivUInt8();
        testGpDivInt16();
        testGpDivUInt16();
        testGpDivInt32();
        testGpDivUInt32();
        testGpDivInt128();
        testGpDivUInt128();
        testGpDivDec();
        testGpDivDec32();
        console.print("<<<< Finished GpDivTests <<<<<");
    }

    void testGpDivInt() {
        Int value1 = 0;
        Int value2 = 0x0AAAAAAAAAAAAAAA;
        Int value3 = 5;
        value1 = value2 / value3;
        assert value1 == 0x222222222222222; // 153722867280912930
    }

    void testGpDivIntConstants() {
        Int value = 0x0AAAAAAAAAAAAAAA / 5;
        assert value == 0x222222222222222; // 153722867280912930
    }

    void testGpDivUInt() {
        UInt value1 = 0;
        UInt value2 = 0xFAAAAAAAAAAAAAAA;
        UInt value3 = 5;
        value1 = value2 / value3;
        assert value1 == 0x3222222222222222; // 3612487381101453858
    }

    void testGpDivInt8() {
        Int8 value1 = 0;
        Int8 value2 = 0x5F;
        Int8 value3 = 5;
        value1 = value2 / value3;
        assert value1 == 0x13; // 19
    }

    void testGpDivUInt8() {
        UInt8 value1 = 0;
        UInt8 value2 = 0xFF;
        UInt8 value3 = 5;
        value1 = value2 / value3;
        assert value1 == 0x33; // 51
    }

    void testGpDivInt16() {
        Int16 value1 = 0;
        Int16 value2 = 0x5FFF;
        Int16 value3 = 5;
        value1 = value2 / value3;
        assert value1 == 0x1333; // 4915
    }

    void testGpDivUInt16() {
        UInt16 value1 = 0;
        UInt16 value2 = 0xFFFF;
        UInt16 value3 = 5;
        value1 = value2 / value3;
        assert value1 == 0x3333; // 13107
    }

    void testGpDivInt32() {
        Int32 value1 = 0;
        Int32 value2 = 0x5FFFFFFF;
        Int32 value3 = 5;
        value1 = value2 / value3;
        assert value1 == 0x13333333; // 322122547
    }

    void testGpDivUInt32() {
        UInt32 value1 = 0;
        UInt32 value2 = 0xFFFFFFFF;
        UInt32 value3 = 5;
        value1 = value2 / value3;
        assert value1 == 0x33333333; // 858993459
    }

    void testGpDivInt128() {
//        Int128 value1 = 0;
//        Int128 value2 = 0x5FFFFFFF;
//        Int128 value3 = 5;
//        value1 = value2 / value3;
//        assert value1 == 0x13333333;
    }

    void testGpDivUInt128() {
//        UInt128 value1 = 0;
//        UInt128 value2 = 0xFFFFFFFF;
//        UInt128 value3 = 5;
//        value1 = value2 / value3;
//        assert value1 == 0x33333333;
    }

    void testGpDivDec() {
//        Dec value1 = 0;
//        Dec value2 = 12.75;
//        Dec value3 = 3.0;
//        value1 = value2 / value3;
//        assert value1 == 4.25;
    }

    void testGpDivDec32() {
//        Dec32 value1 = 0;
//        Dec32 value2 = 12.75;
//        Dec32 value3 = 3.0;
//        value1 = value2 / value3;
//        assert value1 == 4.25;
    }
}
