/**
 * Tests for the JIT Op IP_Div.java.
 */
class IpDivTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running IpDivTests >>>>");
        testIpDivInt();
        testIpDivUInt();
        testIpDivInt8();
        testIpDivUInt8();
        testIpDivInt16();
        testIpDivUInt16();
        testIpDivInt32();
        testIpDivUInt32();
        testIpDivInt128();
        testIpDivUInt128();
        testIpDivDec();
        testIpDivDec32();
        console.print("<<<< Finished IpDivTests <<<<<");
    }

    void testIpDivInt() {
        Int value1 = 0x0AAAAAAAAAAAAAAA;
        value1 /= 5;
        assert value1 == 0x222222222222222;
    }

    void testIpDivUInt() {
        UInt value1 = 0xFAAAAAAAAAAAAAAA;
        value1 /= 5;
        assert value1 == 0x3222222222222222;
    }

    void testIpDivInt8() {
        Int8 value1 = 0x5F;
        value1 /= 5;
        assert value1 == 0x13;
    }

    void testIpDivUInt8() {
        UInt8 value1 = 0xFF;
        value1 /= 5;
        assert value1 == 0x33;
    }

    void testIpDivInt16() {
        Int16 value1 = 0x5FFF;
        value1 /= 5;
        assert value1 == 0x1333;
    }

    void testIpDivUInt16() {
        UInt16 value1 = 0xFFFF;
        value1 /= 5;
        assert value1 == 0x3333;
    }

    void testIpDivInt32() {
        Int32 value1 = 0x5FFFFFFF;
        value1 /= 5;
        assert value1 == 0x13333333;
    }

    void testIpDivUInt32() {
        UInt32 value1 = 0xFFFFFFFF;
        value1 /= 5;
        assert value1 == 0x33333333;
    }

    void testIpDivInt128() {
//        Int128 value1 = 0x5FFFFFFF;
//        value1 /= 5;
//        assert value1 == 0x13333333;
    }

    void testIpDivUInt128() {
//        UInt128 value1 = 0xFFFFFFFF;
//        value1 /= 5;
//        assert value1 == 0x33333333;
    }

    void testIpDivDec() {
//        Dec value1 = 12.75;
//        value1 /= 3;
//        assert value1 == 4.25;
    }

    void testIpDivDec32() {
//        Dec32 value1 = 12.75;
//        value1 /= 3;
//        assert value1 == 4.25;
    }
}
