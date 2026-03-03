/**
 * Tests for the JIT Op GP_Neg.java.
 */
class GpNegTests {

    @Inject Console console;

    void run() {
        console.print(">>>> Running GpNegTests >>>>");
        testGpNegInt8();
        testGpNegNegativeInt8();
        testGpNegInt16();
        testGpNegNegativeInt16();
        testGpNegInt32();
        testGpNegNegativeInt32();
        testGpNegInt64();
        testGpNegNegativeInt64();
        testGpNegInt128();
        testGpNegNegativeInt128();
        testGpNegDec32();
        testGpNegNegativeDec32();
        testGpNegDec64();
        testGpNegNegativeDec64();
        testGpNegDec128();
        testGpNegNegativeDec128();
        testGpNegFloat32();
        testGpNegNegativeFloat32();
        testGpNegFloat64();
        testGpNegNegativeFloat64();
        console.print("<<<< Finished GpNegTests <<<<<");
    }

    void testGpNegInt8() {
        Int8 value1 = 0;
        Int8 value2 = 127;
        value1 = -value2;
        assert value1 == -127;
    }

    void testGpNegNegativeInt8() {
        Int8 value1 = 0;
        Int8 value2 = -127;
        value1 = -value2;
        assert value1 == 127;
    }

    void testGpNegInt16() {
        Int16 value1 = 0;
        Int16 value2 = 32767;
        value1 = -value2;
        assert value1 == -32767;
    }

    void testGpNegNegativeInt16() {
        Int16 value1 = 0;
        Int16 value2 = -32767;
        value1 = -value2;
        assert value1 == 32767;
    }

    void testGpNegInt32() {
        Int32 value1 = 0;
        Int32 value2 = 2147483647;
        value1 = -value2;
        assert value1 == -2147483647;
    }

    void testGpNegNegativeInt32() {
        Int32 value1 = 0;
        Int32 value2 = -2147483647;
        value1 = -value2;
        assert value1 == 2147483647;
    }

    void testGpNegInt64() {
        Int value1 = 0;
        Int value2 = 9223372036854775807;
        value1 = -value2;
        assert value1 == -9223372036854775807;
    }

    void testGpNegNegativeInt64() {
        Int value1 = 0;
        Int value2 = -9223372036854775807;
        value1 = -value2;
        assert value1 == 9223372036854775807;
    }

    void testGpNegInt128() {
        Int128 value1 = 0;
        Int128 value2 = 922337203685477580812345;
        value1 = -value2;
        assert value1 == -922337203685477580812345;
    }

    void testGpNegNegativeInt128() {
        Int128 value1 = 0;
        Int128 value2 = -922337203685477580812345;
        value1 = -value2;
        assert value1 == 922337203685477580812345;
    }

    void testGpNegDec32() {
        Dec32 value1 = 0;
        Dec32 value2 = 123.456;
        value1 = -value2;
        assert value1 == -123.456;
    }

    void testGpNegNegativeDec32() {
        Dec32 value1 = 0;
        Dec32 value2 = -987.654;
        value1 = -value2;
        assert value1 == 987.654;
    }

    void testGpNegDec64() {
        Dec64 value1 = 0;
        Dec64 value2 = 123.456;
        value1 = -value2;
        assert value1 == -123.456;
    }

    void testGpNegNegativeDec64() {
        Dec64 value1 = 0;
        Dec64 value2 = -987.654;
        value1 = -value2;
        assert value1 == 987.654;
    }

    void testGpNegDec128() {
        Dec128 value1 = 0;
        Dec128 value2 = 123.456;
        value1 = -value2;
        assert value1 == -123.456;
    }

    void testGpNegNegativeDec128() {
        Dec128 value1 = 0;
        Dec128 value2 = -987.654;
        value1 = -value2;
        assert value1 == 987.654;
    }

    void testGpNegFloat32() {
        Float32 value1 = 0;
        Float32 value2 = 123.456;
        value1 = -value2;
        assert value1 == -123.456;
    }

    void testGpNegNegativeFloat32() {
        Float32 value1 = 0;
        Float32 value2 = -987.654;
        value1 = -value2;
        assert value1 == 987.654;
    }

    void testGpNegFloat64() {
        Float64 value1 = 0;
        Float64 value2 = 123.456;
        value1 = -value2;
        assert value1 == -123.456;
    }

    void testGpNegNegativeFloat64() {
        Float64 value1 = 0;
        Float64 value2 = -987.654;
        value1 = -value2;
        assert value1 == 987.654;
    }
}
