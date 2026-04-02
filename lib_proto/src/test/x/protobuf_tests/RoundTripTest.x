import ecstasy.io.ByteArrayInputStream;
import ecstasy.io.ByteArrayOutputStream;

import protobuf.CodedInput;
import protobuf.CodedOutput;
import protobuf.WireType;

/**
 * Round-trip tests that serialize values with CodedOutput and deserialize them with CodedInput,
 * verifying the original value is recovered.
 */
class RoundTripTest {

    // ----- int32 ---------------------------------------------------------------------------------

    @Test
    void shouldRoundTripInt32Values() {
        for (Int32 value : [0, 1, -1, 127, -128, 255, 256,
                32767, -32768, 2147483647, -2147483648]) {
            Byte[]     bytes  = encode(o -> o.writeInt32(1, value));
            CodedInput input  = decode(bytes);
            Int        tag    = input.readTag();
            assert WireType.getFieldNumber(tag) == 1;
            assert WireType.getWireType(tag) == WireType.VARINT;
            assert input.readInt32() == value as $"int32 round-trip failed for {value}";
        }
    }

    // ----- int64 ---------------------------------------------------------------------------------

    @Test
    void shouldRoundTripInt64Values() {
        for (Int64 value : [0, 1, -1, 2147483647, -2147483648, 2147483648, -2147483649]) {
            Byte[]     bytes = encode(o -> o.writeInt64(1, value));
            CodedInput input = decode(bytes);
            input.readTag();
            assert input.readInt64() == value as $"int64 round-trip failed for {value}";
        }
    }

    // ----- uint32 --------------------------------------------------------------------------------

    @Test
    void shouldRoundTripUInt32Values() {
        for (UInt32 value : [0, 1, 127, 128, 255, 256, 65535, 4294967295]) {
            Byte[]     bytes = encode(o -> o.writeUInt32(1, value));
            CodedInput input = decode(bytes);
            input.readTag();
            assert input.readUInt32() == value as $"uint32 round-trip failed for {value}";
        }
    }

    // ----- uint64 --------------------------------------------------------------------------------

    @Test
    void shouldRoundTripUInt64Values() {
        for (UInt64 value : [0, 1, 255, 65535, 4294967295, 4294967296]) {
            Byte[]     bytes = encode(o -> o.writeUInt64(1, value));
            CodedInput input = decode(bytes);
            input.readTag();
            assert input.readUInt64() == value as $"uint64 round-trip failed for {value}";
        }
    }

    // ----- sint32 --------------------------------------------------------------------------------

    @Test
    void shouldRoundTripSInt32Values() {
        for (Int32 value : [0, 1, -1, 2, -2, 127, -128, 32767, -32768, 2147483647, -2147483648]) {
            Byte[]     bytes = encode(o -> o.writeSInt32(1, value));
            CodedInput input = decode(bytes);
            input.readTag();
            assert input.readSInt32() == value as $"sint32 round-trip failed for {value}";
        }
    }

    // ----- sint64 --------------------------------------------------------------------------------

    @Test
    void shouldRoundTripSInt64Values() {
        for (Int64 value : [0, 1, -1, 2, -2, 2147483647, -2147483648, 2147483648, -2147483649]) {
            Byte[]     bytes = encode(o -> o.writeSInt64(1, value));
            CodedInput input = decode(bytes);
            input.readTag();
            assert input.readSInt64() == value as $"sint64 round-trip failed for {value}";
        }
    }

    // ----- fixed32 -------------------------------------------------------------------------------

    @Test
    void shouldRoundTripFixed32Values() {
        for (UInt32 value : [0, 1, 255, 256, 0x12345678, 0xFFFFFFFF]) {
            Byte[]     bytes = encode(o -> o.writeFixed32(1, value));
            CodedInput input = decode(bytes);
            Int        tag   = input.readTag();
            assert WireType.getWireType(tag) == WireType.I32;
            assert input.readFixed32() == value as $"fixed32 round-trip failed for {value}";
        }
    }

    // ----- fixed64 -------------------------------------------------------------------------------

    @Test
    void shouldRoundTripFixed64Values() {
        for (UInt64 value : [0, 1, 0xFFFFFFFF, 0x100000000, 0x123456789ABCDEF0]) {
            Byte[]     bytes = encode(o -> o.writeFixed64(1, value));
            CodedInput input = decode(bytes);
            Int        tag   = input.readTag();
            assert WireType.getWireType(tag) == WireType.I64;
            assert input.readFixed64() == value as $"fixed64 round-trip failed for {value}";
        }
    }

    // ----- sfixed32 ------------------------------------------------------------------------------

    @Test
    void shouldRoundTripSFixed32Values() {
        for (Int32 value : [0, 1, -1, 127, -128, 2147483647, -2147483648]) {
            Byte[]     bytes = encode(o -> o.writeSFixed32(1, value));
            CodedInput input = decode(bytes);
            input.readTag();
            assert input.readSFixed32() == value as $"sfixed32 round-trip failed for {value}";
        }
    }

    // ----- sfixed64 ------------------------------------------------------------------------------

    @Test
    void shouldRoundTripSFixed64Values() {
        for (Int64 value : [0, 1, -1, 2147483647, -2147483648, 2147483648, -2147483649]) {
            Byte[]     bytes = encode(o -> o.writeSFixed64(1, value));
            CodedInput input = decode(bytes);
            input.readTag();
            assert input.readSFixed64() == value as $"sfixed64 round-trip failed for {value}";
        }
    }

    // ----- bool ----------------------------------------------------------------------------------

    @Test
    void shouldRoundTripBool() {
        for (Boolean value : [True, False]) {
            Byte[]     bytes = encode(o -> o.writeBool(1, value));
            CodedInput input = decode(bytes);
            input.readTag();
            assert input.readBool() == value as $"bool round-trip failed for {value}";
        }
    }

    // ----- string --------------------------------------------------------------------------------

    @Test
    void shouldRoundTripStringValues() {
        for (String value : ["", "hello", "testing", "Hello, Protobuf!"]) {
            Byte[]     bytes = encode(o -> o.writeString(1, value));
            CodedInput input = decode(bytes);
            Int        tag   = input.readTag();
            assert WireType.getWireType(tag) == WireType.LEN;
            assert input.readString() == value as $"string round-trip failed for \"{value}\"";
        }
    }

    @Test
    void shouldRoundTripUnicodeStrings() {
        for (String value : ["\u00e9", "\u00fc\u00f6\u00e4", "\u4e16\u754c"]) {
            Byte[]     bytes = encode(o -> o.writeString(1, value));
            CodedInput input = decode(bytes);
            input.readTag();
            assert input.readString() == value as $"unicode string round-trip failed";
        }
    }

    // ----- bytes ---------------------------------------------------------------------------------

    @Test
    void shouldRoundTripBytesValues() {
        Byte[][] testCases = [[], [0x00], [0xFF], [0x01, 0x02, 0x03], [0xDE, 0xAD, 0xBE, 0xEF]];
        for (Byte[] value : testCases) {
            Byte[]     bytes = encode(o -> o.writeBytes(1, value));
            CodedInput input = decode(bytes);
            Int        tag   = input.readTag();
            assert WireType.getWireType(tag) == WireType.LEN;
            immutable Byte[] result = input.readBytes();
            assert result == value as $"bytes round-trip failed";
        }
    }

    // ----- float / double ------------------------------------------------------------------------

    @Test
    void shouldRoundTripFloatValues() {
        for (Float32 value : [0.0, 1.0, -1.0, 3.14]) {
            Byte[]     bytes = encode(o -> o.writeFloat(1, value));
            CodedInput input = decode(bytes);
            Int        tag   = input.readTag();
            assert WireType.getWireType(tag) == WireType.I32;
            assert input.readFloat() == value as $"float round-trip failed for {value}";
        }
    }

    @Test
    void shouldRoundTripDoubleValues() {
        for (Float64 value : [0.0, 1.0, -1.0, 3.141592653589793]) {
            Byte[]     bytes = encode(o -> o.writeDouble(1, value));
            CodedInput input = decode(bytes);
            Int        tag   = input.readTag();
            assert WireType.getWireType(tag) == WireType.I64;
            assert input.readDouble() == value as $"double round-trip failed for {value}";
        }
    }

    // ----- multi-field messages ------------------------------------------------------------------

    @Test
    void shouldRoundTripMultiFieldMessage() {
        Byte[] bytes = encode(o -> {
            o.writeInt32(1, 42);
            o.writeString(2, "hello");
            o.writeBool(3, True);
            o.writeFixed32(4, 0xDEADBEEF);
            o.writeSInt32(5, -100);
        });

        CodedInput input = decode(bytes);

        Int tag1 = input.readTag();
        assert WireType.getFieldNumber(tag1) == 1;
        assert input.readInt32() == 42;

        Int tag2 = input.readTag();
        assert WireType.getFieldNumber(tag2) == 2;
        assert input.readString() == "hello";

        Int tag3 = input.readTag();
        assert WireType.getFieldNumber(tag3) == 3;
        assert input.readBool() == True;

        Int tag4 = input.readTag();
        assert WireType.getFieldNumber(tag4) == 4;
        assert input.readFixed32() == 0xDEADBEEF;

        Int tag5 = input.readTag();
        assert WireType.getFieldNumber(tag5) == 5;
        assert input.readSInt32() == -100;

        assert input.isAtEnd();
    }

    @Test
    void shouldRoundTripEmbeddedMessage() {
        // Simulate an outer message with an embedded sub-message:
        //   field 1 (int32) = 42
        //   field 2 (LEN)   = sub-message { field 1 (int32) = 150 }
        ByteArrayOutputStream innerBuf = new ByteArrayOutputStream();
        CodedOutput innerOut = new CodedOutput(innerBuf);
        innerOut.writeInt32(1, 150);
        Byte[] innerBytes = innerBuf.bytes.freeze(False);

        Byte[] bytes = encode(o -> {
            o.writeInt32(1, 42);
            o.writeBytes(2, innerBytes);
        });

        CodedInput input = decode(bytes);

        // Read outer field 1
        Int tag1 = input.readTag();
        assert WireType.getFieldNumber(tag1) == 1;
        assert input.readInt32() == 42;

        // Read outer field 2 (sub-message)
        Int tag2 = input.readTag();
        assert WireType.getFieldNumber(tag2) == 2;
        assert WireType.getWireType(tag2) == WireType.LEN;

        Int length   = input.readVarint().toInt();
        Int oldLimit = input.pushLimit(length);

        // Read inner field 1
        Int innerTag = input.readTag();
        assert WireType.getFieldNumber(innerTag) == 1;
        assert input.readInt32() == 150;
        assert input.isAtEnd();

        input.popLimit(oldLimit);
        assert input.isAtEnd();
    }

    // ----- helpers -------------------------------------------------------------------------------

    private immutable Byte[] encode(function void(CodedOutput) writer) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        CodedOutput output = new CodedOutput(buf);
        writer(output);
        return buf.bytes.freeze(inPlace=True);
    }

    private CodedInput decode(Byte[] bytes) =
        new CodedInput(new ByteArrayInputStream(bytes));
}
