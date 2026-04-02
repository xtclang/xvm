import ecstasy.io.ByteArrayInputStream;
import ecstasy.io.ByteArrayOutputStream;

import protobuf.AbstractMessage;
import protobuf.CodedInput;
import protobuf.CodedOutput;
import protobuf.WireType;

class MapFieldTest {

    // ----- map<string, string> -------------------------------------------------------------------

    @Test
    void shouldRoundTripMapStringString() {
        immutable Byte[] bytes = encode(o -> {
            o.writeMapStringString(1, "key1", "value1");
            o.writeMapStringString(1, "key2", "value2");
        });

        CodedInput input = newInput(bytes);

        // First entry
        Int tag1 = input.readTag();
        assert WireType.getFieldNumber(tag1) == 1;
        assert WireType.getWireType(tag1) == WireType.LEN;
        (String k1, String v1) = input.readMapStringString();
        assert k1 == "key1";
        assert v1 == "value1";

        // Second entry
        Int tag2 = input.readTag();
        assert WireType.getFieldNumber(tag2) == 1;
        (String k2, String v2) = input.readMapStringString();
        assert k2 == "key2";
        assert v2 == "value2";

        assert input.isAtEnd();
    }

    @Test
    void shouldComputeMapStringStringSize() {
        immutable Byte[] bytes = encode(o -> o.writeMapStringString(1, "hello", "world"));
        assert CodedOutput.computeMapStringStringSize(1, "hello", "world") == bytes.size;
    }

    // ----- map<string, int32> --------------------------------------------------------------------

    @Test
    void shouldRoundTripMapStringInt32() {
        immutable Byte[] bytes = encode(o -> {
            o.writeMapStringInt32(1, "count", 42);
            o.writeMapStringInt32(1, "max", 100);
        });

        CodedInput input = newInput(bytes);

        Int tag1 = input.readTag();
        assert WireType.getFieldNumber(tag1) == 1;
        (String k1, Int32 v1) = input.readMapStringInt32();
        assert k1 == "count";
        assert v1 == 42;

        input.readTag();
        (String k2, Int32 v2) = input.readMapStringInt32();
        assert k2 == "max";
        assert v2 == 100;
    }

    @Test
    void shouldComputeMapStringInt32Size() {
        immutable Byte[] bytes = encode(o -> o.writeMapStringInt32(1, "count", 42));
        assert CodedOutput.computeMapStringInt32Size(1, "count", 42) == bytes.size;
    }

    // ----- map<string, int64> --------------------------------------------------------------------

    @Test
    void shouldRoundTripMapStringInt64() {
        immutable Byte[] bytes = encode(o -> o.writeMapStringInt64(1, "big", 1_000_000_000_000));

        CodedInput input = newInput(bytes);
        input.readTag();
        (String k, Int64 v) = input.readMapStringInt64();
        assert k == "big";
        assert v == 1_000_000_000_000;
    }

    @Test
    void shouldComputeMapStringInt64Size() {
        immutable Byte[] bytes = encode(o -> o.writeMapStringInt64(1, "big", 1_000_000_000_000));
        assert CodedOutput.computeMapStringInt64Size(1, "big", 1_000_000_000_000) == bytes.size;
    }

    // ----- map<string, bytes> --------------------------------------------------------------------

    @Test
    void shouldRoundTripMapStringBytes() {
        Byte[] data = [0xDE, 0xAD, 0xBE, 0xEF];
        immutable Byte[] bytes = encode(o -> o.writeMapStringBytes(1, "payload", data));

        CodedInput input = newInput(bytes);
        input.readTag();
        (String k, immutable Byte[] v) = input.readMapStringBytes();
        assert k == "payload";
        assert v == data;
    }

    @Test
    void shouldComputeMapStringBytesSize() {
        Byte[] data = [0xDE, 0xAD];
        immutable Byte[] bytes = encode(o -> o.writeMapStringBytes(1, "x", data));
        assert CodedOutput.computeMapStringBytesSize(1, "x", data) == bytes.size;
    }

    // ----- map<int32, string> --------------------------------------------------------------------

    @Test
    void shouldRoundTripMapInt32String() {
        immutable Byte[] bytes = encode(o -> {
            o.writeMapInt32String(1, 1, "one");
            o.writeMapInt32String(1, 2, "two");
        });

        CodedInput input = newInput(bytes);

        input.readTag();
        (Int32 k1, String v1) = input.readMapInt32String();
        assert k1 == 1;
        assert v1 == "one";

        input.readTag();
        (Int32 k2, String v2) = input.readMapInt32String();
        assert k2 == 2;
        assert v2 == "two";
    }

    @Test
    void shouldComputeMapInt32StringSize() {
        immutable Byte[] bytes = encode(o -> o.writeMapInt32String(1, 42, "hello"));
        assert CodedOutput.computeMapInt32StringSize(1, 42, "hello") == bytes.size;
    }

    // ----- map<int32, int32> ---------------------------------------------------------------------

    @Test
    void shouldRoundTripMapInt32Int32() {
        immutable Byte[] bytes = encode(o -> o.writeMapInt32Int32(1, 10, 20));

        CodedInput input = newInput(bytes);
        input.readTag();
        (Int32 k, Int32 v) = input.readMapInt32Int32();
        assert k == 10;
        assert v == 20;
    }

    @Test
    void shouldComputeMapInt32Int32Size() {
        immutable Byte[] bytes = encode(o -> o.writeMapInt32Int32(1, 10, 20));
        assert CodedOutput.computeMapInt32Int32Size(1, 10, 20) == bytes.size;
    }

    // ----- map<int64, string> --------------------------------------------------------------------

    @Test
    void shouldRoundTripMapInt64String() {
        immutable Byte[] bytes = encode(o -> o.writeMapInt64String(1, 999_999_999_999, "large"));

        CodedInput input = newInput(bytes);
        input.readTag();
        (Int64 k, String v) = input.readMapInt64String();
        assert k == 999_999_999_999;
        assert v == "large";
    }

    @Test
    void shouldComputeMapInt64StringSize() {
        immutable Byte[] bytes = encode(o -> o.writeMapInt64String(1, 999_999_999_999, "large"));
        assert CodedOutput.computeMapInt64StringSize(1, 999_999_999_999, "large") == bytes.size;
    }

    // ----- map<int64, int64> ---------------------------------------------------------------------

    @Test
    void shouldRoundTripMapInt64Int64() {
        immutable Byte[] bytes = encode(o -> o.writeMapInt64Int64(1, 100, 200));

        CodedInput input = newInput(bytes);
        input.readTag();
        (Int64 k, Int64 v) = input.readMapInt64Int64();
        assert k == 100;
        assert v == 200;
    }

    @Test
    void shouldComputeMapInt64Int64Size() {
        immutable Byte[] bytes = encode(o -> o.writeMapInt64Int64(1, 100, 200));
        assert CodedOutput.computeMapInt64Int64Size(1, 100, 200) == bytes.size;
    }

    // ----- map<bool, string> ---------------------------------------------------------------------

    @Test
    void shouldRoundTripMapBoolString() {
        immutable Byte[] bytes = encode(o -> {
            o.writeMapBoolString(1, True, "yes");
            o.writeMapBoolString(1, False, "no");
        });

        CodedInput input = newInput(bytes);

        input.readTag();
        (Boolean k1, String v1) = input.readMapBoolString();
        assert k1 == True;
        assert v1 == "yes";

        input.readTag();
        (Boolean k2, String v2) = input.readMapBoolString();
        assert k2 == False;
        assert v2 == "no";
    }

    @Test
    void shouldComputeMapBoolStringSize() {
        immutable Byte[] bytes = encode(o -> o.writeMapBoolString(1, True, "yes"));
        assert CodedOutput.computeMapBoolStringSize(1, True, "yes") == bytes.size;
    }

    // ----- map with other fields -----------------------------------------------------------------

    @Test
    void shouldRoundTripMapWithOtherFields() {
        immutable Byte[] bytes = encode(o -> {
            o.writeInt32(1, 42);
            o.writeMapStringString(2, "name", "Alice");
            o.writeMapStringString(2, "city", "London");
            o.writeString(3, "footer");
        });

        CodedInput input = newInput(bytes);

        // Field 1: int32
        input.readTag();
        assert input.readInt32() == 42;

        // Field 2: first map entry
        input.readTag();
        (String k1, String v1) = input.readMapStringString();
        assert k1 == "name";
        assert v1 == "Alice";

        // Field 2: second map entry
        input.readTag();
        (String k2, String v2) = input.readMapStringString();
        assert k2 == "city";
        assert v2 == "London";

        // Field 3: string
        input.readTag();
        assert input.readString() == "footer";
    }

    @Test
    void shouldComputeCorrectTotalSizeForMaps() {
        immutable Byte[] bytes = encode(o -> {
            o.writeInt32(1, 42);
            o.writeMapStringInt32(2, "count", 10);
            o.writeMapStringInt32(2, "max", 100);
        });

        Int expectedSize = CodedOutput.computeInt32Size(1, 42)
                         + CodedOutput.computeMapStringInt32Size(2, "count", 10)
                         + CodedOutput.computeMapStringInt32Size(2, "max", 100);
        assert expectedSize == bytes.size;
    }

    // ----- unknown field round-trip --------------------------------------------------------------

    @Test
    void shouldRoundTripMapAsUnknownFields() {
        // Map entries are just LEN-delimited sub-messages, so AbstractMessage
        // should round-trip them faithfully as unknown fields
        immutable Byte[] bytes = encode(o -> {
            o.writeMapStringString(1, "key", "value");
        });

        AbstractMessage msg = new AbstractMessage();
        msg.mergeFrom(newInput(bytes));
        assert msg.toByteArray() == bytes;
    }

    // ----- wire format verification --------------------------------------------------------------

    @Test
    void shouldProduceCorrectMapEntryBytes() {
        // map<string, string> field 1, key="a", value="b"
        // Outer: tag(1, LEN) + length + inner
        // Inner: tag(1, LEN) + length(1) + "a" + tag(2, LEN) + length(1) + "b"
        //   tag(1, LEN) = 0x0A, length "a" = 0x01, 'a' = 0x61
        //   tag(2, LEN) = 0x12, length "b" = 0x01, 'b' = 0x62
        //   inner = 6 bytes
        // Outer: tag(1, LEN) = 0x0A, length = 0x06
        immutable Byte[] bytes = encode(o -> o.writeMapStringString(1, "a", "b"));
        assert bytes == [0x0A, 0x06, 0x0A, 0x01, 0x61, 0x12, 0x01, 0x62];
    }

    // ----- helper --------------------------------------------------------------------------------

    private immutable Byte[] encode(function void(CodedOutput) writer) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        CodedOutput out = new CodedOutput(buf);
        writer(out);
        return buf.bytes.freeze(inPlace=True);
    }

    private CodedInput newInput(Byte[] bytes) =
        new CodedInput(new ByteArrayInputStream(bytes));
}
