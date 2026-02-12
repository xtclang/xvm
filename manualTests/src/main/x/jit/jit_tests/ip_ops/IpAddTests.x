/**
 * Tests for the JIT Op IP_Add.java.
 */
class IpAddTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running IpAddTests >>>>");
        testIpAddInt();
        testIpAddUInt();
        testIpAddInt8();
        testIpAddUInt8();
        testIpAddInt16();
        testIpAddUInt16();
        testIpAddInt32();
        testIpAddUInt32();
        testIpAddInt128();
        testIpAddUInt128();
        testIpAddDec();
        testIpAddDec32();
        testIpAddString();
        testIpAddIntToChar();
        console.print("<<<< Finished IpAddTests <<<<<");
    }

    void testIpAddInt() {
        Int value1 = 10;
        value1 += 19;
        assert value1 == 29;
    }

    void testIpAddUInt() {
        UInt value1 = 0xFFFFFFF0;
        value1 += 1;
        assert value1 == 0xFFFFFFF1;
    }

    void testIpAddInt8() {
        Int8 value1 = 10;
        value1 += 19;
        assert value1 == 29;
    }

    void testIpAddUInt8() {
        UInt8 value1 = 0xF0;
        value1 += 1;
        assert value1 == 0xF1;
    }

    void testIpAddInt16() {
        Int16 value1 = 10;
        value1 += 19;
        assert value1 == 29;
    }

    void testIpAddUInt16() {
        UInt16 value1 = 0xFFF0;
        value1 += 1;
        assert value1 == 0xFFF1;
    }

    void testIpAddInt32() {
        Int32 value1 = 10;
        value1 += 19;
        assert value1 == 29;
    }

    void testIpAddUInt32() {
        UInt32 value1 = 0xFFFFFFF0;
        value1 += 1;
        assert value1 == 0xFFFFFFF1;
    }

    void testIpAddInt128() {
        Int128 value1 = 10;
        value1 += 19;
        assert value1 == 29;
    }

    void testIpAddUInt128() {
        UInt128 value1 = 0xFFFFFFF0;
        value1 += 1;
        assert value1 == 0xFFFFFFF1;
    }

    void testIpAddDec() {
//        Dec value1 = 2.5;
//        value1 += 1.25;
//        assert value1 == 3.75;
    }

    void testIpAddDec32() {
//        Dec32 value1 = 2.5;
//        value1 += 1.25;
//        assert value1 == 3.75;
    }

    void testIpAddString() {
        String value1 = "a";
        value1 += "b";
//        assert value1 == "ab";
    }

    void testIpAddIntToChar() {
        Char value1 = 'a';
        value1 += 1;
        assert value1 == 'b';
    }
}
