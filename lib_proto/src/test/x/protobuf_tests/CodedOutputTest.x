import ecstasy.io.ByteArrayInputStream;
import ecstasy.io.ByteArrayOutputStream;

import protobuf.CodedInput;
import protobuf.CodedOutput;
import protobuf.WireType;

class CodedOutputTest {

    // ----- varint tests --------------------------------------------------------------------------

    @Test
    void shouldWriteVarintOne() {
        Byte[] bytes = encodeVarint(1);
        assert bytes == [0x01];
    }

    @Test
    void shouldWriteVarint150() {
        // From the protobuf spec: 150 encodes as 0x96 0x01
        Byte[] bytes = encodeVarint(150);
        assert bytes == [0x96, 0x01];
    }

    @Test
    void shouldWriteVarintZero() {
        Byte[] bytes = encodeVarint(0);
        assert bytes == [0x00];
    }

    @Test
    void shouldWriteVarint300() {
        // 300 = 0xAC 0x02
        Byte[] bytes = encodeVarint(300);
        assert bytes == [0xAC, 0x02];
    }

    @Test
    void shouldWriteVarint127() {
        Byte[] bytes = encodeVarint(127);
        assert bytes == [0x7F];
    }

    // ----- tag tests -----------------------------------------------------------------------------

    @Test
    void shouldWriteTag() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        CodedOutput output = new CodedOutput(buf);
        output.writeTag(1, WireType.VARINT);
        assert buf.bytes == [0x08];
    }

    @Test
    void shouldWriteTagForLenField() {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        CodedOutput output = new CodedOutput(buf);
        output.writeTag(2, WireType.LEN);
        // (2 << 3) | 2 = 18 = 0x12
        assert buf.bytes == [0x12];
    }

    // ----- integer field tests -------------------------------------------------------------------

    @Test
    void shouldWriteInt32Field() {
        // From protobuf spec: field 1, int32 = 150 -> 08 96 01
        Byte[] bytes = encode(o -> o.writeInt32(1, 150));
        assert bytes == [0x08, 0x96, 0x01];
    }

    @Test
    void shouldWriteUInt32Field() {
        Byte[] bytes = encode(o -> o.writeUInt32(1, 150));
        assert bytes == [0x08, 0x96, 0x01];
    }

    @Test
    void shouldWriteSInt32PositiveField() {
        // ZigZag: 1 encodes as 2
        Byte[] bytes = encode(o -> o.writeSInt32(1, 1));
        assert bytes == [0x08, 0x02];
    }

    @Test
    void shouldWriteSInt32NegativeField() {
        // ZigZag: -1 encodes as 1
        Byte[] bytes = encode(o -> o.writeSInt32(1, -1));
        assert bytes == [0x08, 0x01];
    }

    @Test
    void shouldWriteSInt32NegativeTwoField() {
        // ZigZag: -2 encodes as 3
        Byte[] bytes = encode(o -> o.writeSInt32(1, -2));
        assert bytes == [0x08, 0x03];
    }

    // ----- fixed-width tests ---------------------------------------------------------------------

    @Test
    void shouldWriteFixed32Field() {
        // fixed32 value 1 in little-endian: 01 00 00 00
        // tag for field 1, I32: (1 << 3) | 5 = 13 = 0x0D
        Byte[] bytes = encode(o -> o.writeFixed32(1, 1));
        assert bytes == [0x0D, 0x01, 0x00, 0x00, 0x00];
    }

    @Test
    void shouldWriteFixed32LargeValue() {
        // 0x12345678 in little-endian: 78 56 34 12
        Byte[] bytes = encode(o -> o.writeFixed32(1, 0x12345678));
        assert bytes == [0x0D, 0x78, 0x56, 0x34, 0x12];
    }

    @Test
    void shouldWriteFixed64Field() {
        // fixed64 value 1 in little-endian 8 bytes
        // tag for field 1, I64: (1 << 3) | 1 = 9 = 0x09
        Byte[] bytes = encode(o -> o.writeFixed64(1, 1));
        assert bytes == [0x09, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00];
    }

    @Test
    void shouldWriteSFixed32Field() {
        // sfixed32 -1 in little-endian: FF FF FF FF
        Byte[] bytes = encode(o -> o.writeSFixed32(1, -1));
        assert bytes == [0x0D, 0xFF, 0xFF, 0xFF, 0xFF];
    }

    // ----- bool tests ----------------------------------------------------------------------------

    @Test
    void shouldWriteBoolTrue() {
        Byte[] bytes = encode(o -> o.writeBool(1, True));
        assert bytes == [0x08, 0x01];
    }

    @Test
    void shouldWriteBoolFalse() {
        Byte[] bytes = encode(o -> o.writeBool(1, False));
        assert bytes == [0x08, 0x00];
    }

    // ----- length-delimited tests ----------------------------------------------------------------

    @Test
    void shouldWriteStringField() {
        // From protobuf spec: field 2, string "testing" -> 12 07 74 65 73 74 69 6e 67
        Byte[] bytes = encode(o -> o.writeString(2, "testing"));
        assert bytes == [0x12, 0x07, 0x74, 0x65, 0x73, 0x74, 0x69, 0x6E, 0x67];
    }

    @Test
    void shouldWriteEmptyStringField() {
        Byte[] bytes = encode(o -> o.writeString(1, ""));
        // tag 0x08 is VARINT for field 1; string uses LEN, tag = (1<<3)|2 = 0x0A
        assert bytes == [0x0A, 0x00];
    }

    @Test
    void shouldWriteBytesField() {
        Byte[] bytes = encode(o -> o.writeBytes(1, [0xAA, 0xBB, 0xCC]));
        // tag for field 1, LEN: 0x0A, length 3
        assert bytes == [0x0A, 0x03, 0xAA, 0xBB, 0xCC];
    }

    // ----- round-trip tests ----------------------------------------------------------------------

    @Test
    void shouldRoundTripInt32() {
        Byte[] bytes = encode(o -> o.writeInt32(1, 150));
        CodedInput input = newCodedInput(bytes);
        Int tag = input.readTag();
        assert WireType.getFieldNumber(tag) == 1;
        assert WireType.getWireType(tag) == WireType.VARINT;
        assert input.readInt32() == 150;
    }

    @Test
    void shouldRoundTripString() {
        Byte[] bytes = encode(o -> o.writeString(2, "testing"));
        CodedInput input = newCodedInput(bytes);
        Int tag = input.readTag();
        assert WireType.getFieldNumber(tag) == 2;
        assert WireType.getWireType(tag) == WireType.LEN;
        assert input.readString() == "testing";
    }

    @Test
    void shouldRoundTripFixed32() {
        Byte[] bytes = encode(o -> o.writeFixed32(3, 0x12345678));
        CodedInput input = newCodedInput(bytes);
        Int tag = input.readTag();
        assert WireType.getFieldNumber(tag) == 3;
        assert WireType.getWireType(tag) == WireType.I32;
        assert input.readFixed32() == 0x12345678;
    }

    @Test
    void shouldRoundTripSInt32() {
        for (Int32 value : [0, 1, -1, 2, -2, 127, -128, 32767, -32768]) {
            Byte[] bytes = encode(o -> o.writeSInt32(1, value));
            CodedInput input = newCodedInput(bytes);
            input.readTag();
            assert input.readSInt32() == value;
        }
    }

    @Test
    void shouldRoundTripBool() {
        Byte[] trueBytes  = encode(o -> o.writeBool(1, True));
        Byte[] falseBytes = encode(o -> o.writeBool(1, False));

        CodedInput trueInput = newCodedInput(trueBytes);
        trueInput.readTag();
        assert trueInput.readBool() == True;

        CodedInput falseInput = newCodedInput(falseBytes);
        falseInput.readTag();
        assert falseInput.readBool() == False;
    }

    @Test
    void shouldRoundTripMultipleFields() {
        // Simulate a message with field 1 (int32=150), field 2 (string="testing")
        Byte[] bytes = encode(o -> {
            o.writeInt32(1, 150);
            o.writeString(2, "testing");
        });

        CodedInput input = newCodedInput(bytes);

        Int tag1 = input.readTag();
        assert WireType.getFieldNumber(tag1) == 1;
        assert input.readInt32() == 150;

        Int tag2 = input.readTag();
        assert WireType.getFieldNumber(tag2) == 2;
        assert input.readString() == "testing";

        assert input.isAtEnd();
    }

    // ----- size computation tests ----------------------------------------------------------------

    @Test
    void shouldComputeVarintSize() {
        assert CodedOutput.computeVarintSize(0)     == 1;
        assert CodedOutput.computeVarintSize(1)     == 1;
        assert CodedOutput.computeVarintSize(127)   == 1;
        assert CodedOutput.computeVarintSize(128)   == 2;
        assert CodedOutput.computeVarintSize(150)   == 2;
        assert CodedOutput.computeVarintSize(16383) == 2;
        assert CodedOutput.computeVarintSize(16384) == 3;
    }

    // ----- helpers -------------------------------------------------------------------------------

    private immutable Byte[] encodeVarint(Int64 value) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        CodedOutput output = new CodedOutput(buf);
        output.writeVarint(value);
        return buf.bytes.freeze(inPlace=True);
    }

    private immutable Byte[] encode(function void(CodedOutput) writer) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        CodedOutput output = new CodedOutput(buf);
        writer(output);
        return buf.bytes.freeze(inPlace=True);
    }

    private CodedInput newCodedInput(Byte[] bytes) =
        new CodedInput(new ByteArrayInputStream(bytes));
}
