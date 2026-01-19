/**
 * Tests for the JIT Op GP_Compl.java.
 */
class GpComplTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running GpComplTests >>>>");
        testGpComplInt();
        testGpComplUInt();
        testGpComplInt8();
        testGpComplUInt8();
        testGpComplInt16();
        testGpComplUInt16();
        testGpComplInt32();
        testGpComplUInt32();
        console.print("<<<< Finished GpComplTests <<<<<");
    }

    void testGpComplInt() {
        Int value1 = 0;
        Int value2 = 0x0AAAAAAAAAAAAAAA;
        value1 = ~value2;
        assert value1 == -768614336404564651;
    }

    void testGpComplIntConstant() {
        Int value = ~0x0AAAAAAAAAAAAAAA;
        assert value == -768614336404564651;
    }

    void testGpComplUInt() {
        UInt value1 = 0;
        UInt value2 = 0xFAAAAAAAAAAAAAAA;
        value1 = ~value2;
        assert value1 == 0x555555555555555;
    }

    void testGpComplInt8() {
        Int8 value1 = 0;
        Int8 value2 = 0x5F;
        value1 = ~value2;
        assert value1 == -96;
    }

    void testGpComplUInt8() {
        UInt8 value1 = 0;
        UInt8 value2 = 0x55;
        value1 = ~value2;
        assert value1 == 0xAA;
    }

    void testGpComplInt16() {
        Int16 value1 = 0;
        Int16 value2 = 0x5F5F;
        value1 = ~value2;
        assert value1 == -24416; //0xA0A0;
    }

    void testGpComplUInt16() {
        UInt16 value1 = 0;
        UInt16 value2 = 0xF5F5;
        value1 = ~value2;
        assert value1 == 0x0A0A;
    }

    void testGpComplInt32() {
        Int32 value1 = 0;
        Int32 value2 = 0x5ABC5432;
        value1 = ~value2;
        assert value1 == -1522291763;
    }

    void testGpComplUInt32() {
        UInt32 value1 = 0;
        UInt32 value2 = 0xFABC5432;
        UInt32 value3 = 5;
        value1 = ~value2;
        assert value1 == 0x0543ABCD;
    }
}
