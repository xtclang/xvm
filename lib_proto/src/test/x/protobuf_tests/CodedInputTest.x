import ecstasy.io.ByteArrayInputStream;

import protobuf.CodedInput;
import protobuf.WireType;

class CodedInputTest {

    // ----- varint tests --------------------------------------------------------------------------

    @Test
    void shouldReadSingleByteVarint() {
        // varint 1 = 0x01
        CodedInput input = newCodedInput([0x01]);
        assert input.readVarint() == 1;
    }

    @Test
    void shouldReadTwoByteVarint() {
        // varint 150 = 0x96 0x01 (from the protobuf spec example)
        CodedInput input = newCodedInput([0x96, 0x01]);
        assert input.readVarint() == 150;
    }

    @Test
    void shouldReadVarintZero() {
        CodedInput input = newCodedInput([0x00]);
        assert input.readVarint() == 0;
    }

    @Test
    void shouldReadVarint300() {
        // 300 = 0b100101100 -> varint bytes: 0xAC 0x02
        CodedInput input = newCodedInput([0xAC, 0x02]);
        assert input.readVarint() == 300;
    }

    @Test
    void shouldReadMaxSingleByteVarint() {
        // 127 = 0x7F (maximum value in a single byte)
        CodedInput input = newCodedInput([0x7F]);
        assert input.readVarint() == 127;
    }

    // ----- tag tests -----------------------------------------------------------------------------

    @Test
    void shouldReadTag() {
        // Tag for field 1, VARINT: (1 << 3) | 0 = 8 = 0x08
        CodedInput input = newCodedInput([0x08]);
        Int tag = input.readTag();
        assert WireType.getFieldNumber(tag) == 1;
        assert WireType.getWireType(tag) == WireType.VARINT;
    }

    @Test
    void shouldReadTagForLenField() {
        // Tag for field 2, LEN: (2 << 3) | 2 = 18 = 0x12
        CodedInput input = newCodedInput([0x12]);
        Int tag = input.readTag();
        assert WireType.getFieldNumber(tag) == 2;
        assert WireType.getWireType(tag) == WireType.LEN;
    }

    @Test
    void shouldReturnZeroAtEnd() {
        CodedInput input = newCodedInput([]);
        assert input.readTag() == 0;
    }

    // ----- integer type tests --------------------------------------------------------------------

    @Test
    void shouldReadInt32() {
        // varint 150
        CodedInput input = newCodedInput([0x96, 0x01]);
        assert input.readInt32() == 150;
    }

    @Test
    void shouldReadUInt32() {
        CodedInput input = newCodedInput([0x96, 0x01]);
        assert input.readUInt32() == 150;
    }

    @Test
    void shouldReadSInt32Positive() {
        // ZigZag: 1 encoded as 2 (varint 0x02)
        CodedInput input = newCodedInput([0x02]);
        assert input.readSInt32() == 1;
    }

    @Test
    void shouldReadSInt32Negative() {
        // ZigZag: -1 encoded as 1 (varint 0x01)
        CodedInput input = newCodedInput([0x01]);
        assert input.readSInt32() == -1;
    }

    @Test
    void shouldReadSInt32Zero() {
        // ZigZag: 0 encoded as 0
        CodedInput input = newCodedInput([0x00]);
        assert input.readSInt32() == 0;
    }

    @Test
    void shouldReadSInt32NegativeTwo() {
        // ZigZag: -2 encoded as 3 (varint 0x03)
        CodedInput input = newCodedInput([0x03]);
        assert input.readSInt32() == -2;
    }

    // ----- fixed-width tests ---------------------------------------------------------------------

    @Test
    void shouldReadFixed32() {
        // 1 in little-endian: 0x01 0x00 0x00 0x00
        CodedInput input = newCodedInput([0x01, 0x00, 0x00, 0x00]);
        assert input.readFixed32() == 1;
    }

    @Test
    void shouldReadFixed32LargeValue() {
        // 0x12345678 in little-endian: 0x78 0x56 0x34 0x12
        CodedInput input = newCodedInput([0x78, 0x56, 0x34, 0x12]);
        assert input.readFixed32() == 0x12345678;
    }

    @Test
    void shouldReadFixed64() {
        // 1 in little-endian 8 bytes
        CodedInput input = newCodedInput([0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00]);
        assert input.readFixed64() == 1;
    }

    @Test
    void shouldReadSFixed32() {
        // -1 in little-endian: 0xFF 0xFF 0xFF 0xFF
        CodedInput input = newCodedInput([0xFF, 0xFF, 0xFF, 0xFF]);
        assert input.readSFixed32() == -1;
    }

    // ----- bool test -----------------------------------------------------------------------------

    @Test
    void shouldReadBoolTrue() {
        CodedInput input = newCodedInput([0x01]);
        assert input.readBool() == True;
    }

    @Test
    void shouldReadBoolFalse() {
        CodedInput input = newCodedInput([0x00]);
        assert input.readBool() == False;
    }

    // ----- length-delimited tests ----------------------------------------------------------------

    @Test
    void shouldReadBytes() {
        // length 3, then bytes [0xAA, 0xBB, 0xCC]
        CodedInput input = newCodedInput([0x03, 0xAA, 0xBB, 0xCC]);
        immutable Byte[] bytes = input.readBytes();
        assert bytes.size == 3;
        assert bytes[0] == 0xAA;
        assert bytes[1] == 0xBB;
        assert bytes[2] == 0xCC;
    }

    @Test
    void shouldReadEmptyBytes() {
        CodedInput input = newCodedInput([0x00]);
        immutable Byte[] bytes = input.readBytes();
        assert bytes.size == 0;
    }

    @Test
    void shouldReadString() {
        // length 7, then "testing" in ASCII/UTF-8
        CodedInput input = newCodedInput([0x07, 0x74, 0x65, 0x73, 0x74, 0x69, 0x6E, 0x67]);
        assert input.readString() == "testing";
    }

    @Test
    void shouldReadEmptyString() {
        CodedInput input = newCodedInput([0x00]);
        assert input.readString() == "";
    }

    // ----- sub-message limit tests ---------------------------------------------------------------

    @Test
    void shouldRespectLimit() {
        // Two varints: 150 (0x96 0x01) and 1 (0x01)
        CodedInput input = newCodedInput([0x96, 0x01, 0x01]);
        Int oldLimit = input.pushLimit(2);
        assert input.readVarint() == 150;
        assert input.isAtEnd();
        input.popLimit(oldLimit);
        assert !input.isAtEnd();
        assert input.readVarint() == 1;
    }

    // ----- complete message test -----------------------------------------------------------------

    @Test
    void shouldReadSimpleMessage() {
        // From the protobuf spec: Test1 { int32 a = 1; } with a = 150
        // Encoded as: 08 96 01
        CodedInput input = newCodedInput([0x08, 0x96, 0x01]);
        Int tag = input.readTag();
        assert WireType.getFieldNumber(tag) == 1;
        assert WireType.getWireType(tag) == WireType.VARINT;
        assert input.readInt32() == 150;
        assert input.isAtEnd();
    }

    @Test
    void shouldReadStringMessage() {
        // From the protobuf spec: Test2 { string b = 2; } with b = "testing"
        // Encoded as: 12 07 74 65 73 74 69 6e 67
        CodedInput input = newCodedInput([0x12, 0x07, 0x74, 0x65, 0x73, 0x74, 0x69, 0x6E, 0x67]);
        Int tag = input.readTag();
        assert WireType.getFieldNumber(tag) == 2;
        assert WireType.getWireType(tag) == WireType.LEN;
        assert input.readString() == "testing";
        assert input.isAtEnd();
    }

    @Test
    void shouldReadEmbeddedMessage() {
        // From the protobuf spec: Test3 { Test1 c = 3; } with c.a = 150
        // Encoded as: 1a 03 08 96 01
        CodedInput input = newCodedInput([0x1A, 0x03, 0x08, 0x96, 0x01]);
        Int outerTag = input.readTag();
        assert WireType.getFieldNumber(outerTag) == 3;
        assert WireType.getWireType(outerTag) == WireType.LEN;

        // Read the sub-message length and push limit
        Int length   = input.readVarint().toInt();
        Int oldLimit = input.pushLimit(length);

        // Read the inner field
        Int innerTag = input.readTag();
        assert WireType.getFieldNumber(innerTag) == 1;
        assert WireType.getWireType(innerTag) == WireType.VARINT;
        assert input.readInt32() == 150;
        assert input.isAtEnd();

        input.popLimit(oldLimit);
        assert input.isAtEnd();
    }

    // ----- helper --------------------------------------------------------------------------------

    private CodedInput newCodedInput(Byte[] bytes) =
        new CodedInput(new ByteArrayInputStream(bytes));
}
