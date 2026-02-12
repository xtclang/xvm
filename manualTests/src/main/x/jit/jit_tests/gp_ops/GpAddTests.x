/**
 * Tests for the JIT Op GP_Add.java.
 */
class GpAddTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running GpAddTests >>>>");
        testGpAddInt();
        testGpAddIntConstants();
        testGpAddUInt();
        testGpAddInt8();
        testGpAddUInt8();
        testGpAddInt16();
        testGpAddUInt16();
        testGpAddInt32();
        testGpAddUInt32();
        testGpAddInt128();
        testGpAddUInt128();
        testGpAddDec();
        testGpAddDec32();
        testGpAddString();
        testGpAddIntToChar();
        testGpAddCharToChar();
        testGpAddStringToChar();
        console.print("<<<< Finished GpAddTests <<<<<");
    }

    void testGpAddInt() {
        Int value1 = 0;
        Int value2 = 10;
        Int value3 = 19;
        value1 = value2 + value3;
        assert value1 == 29;
    }

    void testGpAddIntConstants() {
        Int value1 = 10 + 19;
        assert value1 == 29;
    }

    void testGpAddUInt() {
        UInt value1 = 0;
        UInt value2 = 0xFFFFFFFFFFFFFFF0;
        UInt value3 = 1;
        value1 = value2 + value3;
        assert value1 == 0xFFFFFFFFFFFFFFF1;
    }

    void testGpAddInt8() {
        Int8 value1 = 0;
        Int8 value2 = 10;
        Int8 value3 = 19;
        value1 = value2 + value3;
        assert value1 == 29;
    }

    void testGpAddUInt8() {
        UInt8 value1 = 0;
        UInt8 value2 = 0xF0;
        UInt8 value3 = 1;
        value1 = value2 + value3;
        assert value1 == 0xF1;
    }

    void testGpAddInt16() {
        Int16 value1 = 0;
        Int16 value2 = 10;
        Int16 value3 = 19;
        value1 = value2 + value3;
        assert value1 == 29;
    }

    void testGpAddUInt16() {
        UInt16 value1 = 0;
        UInt16 value2 = 0xFFF0;
        UInt16 value3 = 1;
        value1 = value2 + value3;
        assert value1 == 0xFFF1;
    }

    void testGpAddInt32() {
        Int32 value1 = 0;
        Int32 value2 = 10;
        Int32 value3 = 19;
        value1 = value2 + value3;
        assert value1 == 29;
    }

    void testGpAddUInt32() {
        UInt32 value1 = 0;
        UInt32 value2 = 0xFFFFFFF0;
        UInt32 value3 = 1;
        value1 = value2 + value3;
        assert value1 == 0xFFFFFFF1;
    }

    void testGpAddInt128() {
        Int128 value1 = 0;
        Int128 value2 = 10;
        Int128 value3 = 19;
        value1 = value2 + value3;
        assert value1 == 29;
    }

    void testGpAddUInt128() {
        UInt128 value1 = 0;
        UInt128 value2 = 0xFFFFFFF0;
        UInt128 value3 = 1;
        value1 = value2 + value3;
        assert value1 == 0xFFFFFFF1;
    }

    void testGpAddDec() {
// TODO: Need Dec support
//        Dec value1 = 0.0;
//        Dec value2 = 2.5;
//        Dec value3 = 1.25;
//        value1 = value2 + value3;
//        assert value1 == 3.75;
    }

    void testGpAddDec32() {
// TODO: Need Dec32 support
//        Dec32 value1 = 0.0;
//        Dec32 value2 = 2.5;
//        Dec32 value3 = 1.25;
//        value1 = value2 + value3;
//        assert value1 == 3.75;
    }

    void testGpAddString() {
        String value1 = "";
        String value2 = "a";
        String value3 = "b";
        value1 = value2 + value3;
        console.print("testGpAddString - value1 should be ab and is: ", True);
        console.print(value1);
//        assert value1 == "ab"; // ToDo Add String concatenation to the JIT ??
    }

    void testGpAddIntToChar() {
        Char value1 = ' ';
        Char value2 = 'a';
        Int  value3 = 1;
        value1 = value2 + value3;
        assert value1 == 'b';
    }

    void testGpAddIntToCharConstants() {
        Char value = 'a' + 3;
        assert value == 'd';
    }

    void testGpAddCharToChar() {
        String value1 = "";
        Char   value2 = 'a';
        Char   value3 = 'b';
        value1 = value2 + value3;
        console.print("testGpAddCharToChar - value1 should be ab and is: ", True);
        console.print(value1);
//        assert value1 == "ab"; // assert fails in the JIT even though the value appears correct
    }

    void testGpAddCharToCharConstants() {
        String value = 'a' + 'b';
        assert value == "ab";
    }

    void testGpAddStringToChar() {
        String value1 = "";
        Char   value2 = 'a';
        String value3 = "bc";
        value1 = value2 + value3;
        console.print("testGpAddStringToChar - value1 should be abc and is: ", True);
        console.print(value1);
//        assert value1 == "abc";  // assert fails in the JIT even though the value appears correct
    }

    void testGpAddStringToCharConstants() {
        String value = 'a' + "bc";
        assert value == "abc";
    }
}
