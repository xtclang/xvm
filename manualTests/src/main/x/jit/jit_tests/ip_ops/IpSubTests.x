/**
 * Tests for the JIT Op IP_Sub.java.
 */
class IpSubTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running IpSubTests >>>>");
        testIpSubInt();
        testIpSubUInt();
        testIpSubInt8();
        testIpSubUInt8();
        testIpSubInt16();
        testIpSubUInt16();
        testIpSubInt32();
        testIpSubUInt32();
        testIpSubInt128();
        testIpSubUInt128();
        testIpSubDec();
        testIpSubDec32();
        testIpSubIntFromChar();
        console.print("<<<< Finished IpSubTests <<<<<");
    }

    void testIpSubInt() {
        Int value1 = 76;
        value1 -= 66;
        assert value1 == 10;
    }

    void testIpSubUInt() {
        UInt value1 = 0xFFFFFFF1;
        value1 -= 1;
        assert value1 == 0xFFFFFFF0;
    }

    void testIpSubInt8() {
        Int8 value1 = 76;
        value1 -= 66;
        assert value1 == 10;
    }

    void testIpSubUInt8() {
        UInt8 value1 = 0xF1;
        value1 -= 1;
        assert value1 == 0xF0;
    }

    void testIpSubInt16() {
        Int16 value1 = 76;
        value1 -= 66;
        assert value1 == 10;
    }

    void testIpSubUInt16() {
        UInt16 value1 = 0xFFF1;
        value1 -= 1;
        assert value1 == 0xFFF0;
    }

    void testIpSubInt32() {
        Int32 value1 = 76;
        value1 -= 66;
        assert value1 == 10;
    }

    void testIpSubUInt32() {
        UInt32 value1 = 0xFFFFFFF1;
        value1 -= 1;
        assert value1 == 0xFFFFFFF0;
    }

    void testIpSubInt128() {
//        Int128 value1 = 76;
//        value1 -= 66;
//        assert value1 == 10;
    }

    void testIpSubUInt128() {
//        UInt128 value1 = 0xFFFFFFF1;
//        value1 -= 1;
//        assert value1 == 0xFFFFFFF0;
    }

    void testIpSubDec() {
//        Dec value1 = 3.75;
//        value1 -= 1.25;
//        assert value1 == 2.5;
    }

    void testIpSubDec32() {
//        Dec32 value1 = 3.75;
//        value1 -= 1.25;
//        assert value1 == 2.5;
    }

    void testIpSubIntFromChar() {
        Char value1 = 'd';
        value1 -= 3;
        assert value1 == 'a';
    }
}
