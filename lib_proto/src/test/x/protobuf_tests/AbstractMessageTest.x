import ecstasy.io.ByteArrayInputStream;
import ecstasy.io.ByteArrayOutputStream;

import protobuf.AbstractMessage;
import protobuf.CodedInput;
import protobuf.CodedOutput;
import protobuf.WireType;

class AbstractMessageTest {

    /**
     * A concrete subclass that handles field 1 (int32) and field 2 (string) as known fields. All
     * other fields pass through as unknown.
     */
    static class TestMessage
            extends AbstractMessage {

        construct() {
            construct AbstractMessage();
        }

        @Override
        construct(TestMessage other) {
            construct AbstractMessage(other);
            id   = other.id;
            name = other.name;
        }

        Int32  id   = 0;
        String name = "";

        @Override
        Boolean parseField(CodedInput input, Int tag) {
            switch (WireType.getFieldNumber(tag)) {
            case 1:
                id = input.readInt32();
                return True;
            case 2:
                name = input.readString();
                return True;
            }
            return False;
        }

        @Override
        void writeKnownFields(CodedOutput out) {
            if (id != 0) {
                out.writeInt32(1, id);
            }
            if (name.size > 0) {
                out.writeString(2, name);
            }
        }

        @Override
        Int knownFieldsSize() {
            Int size = 0;
            if (id != 0) {
                size += CodedOutput.computeInt32Size(1, id);
            }
            if (name.size > 0) {
                size += CodedOutput.computeStringSize(2, name);
            }
            return size;
        }
    }

    // ----- base class: all fields unknown --------------------------------------------------------

    @Test
    void shouldRoundTripAllUnknown() {
        // Serialize with CodedOutput, deserialize into bare AbstractMessage
        immutable Byte[] original = encode(o -> {
            o.writeInt32(1, 42);
            o.writeString(2, "hello");
            o.writeFixed32(3, 0xDEADBEEF);
        });

        AbstractMessage msg = new AbstractMessage();
        msg.mergeFromBytes(original);

        // All fields are unknown, but serialization should reproduce the same bytes
        immutable Byte[] result = msg.toByteArray();
        assert result == original;
    }

    @Test
    void shouldRoundTripEmptyMessage() {
        AbstractMessage msg = new AbstractMessage();
        assert msg.serializedSize() == 0;
        assert msg.toByteArray().size == 0;
    }

    @Test
    void shouldRoundTripSpecExample1() {
        // Test1 { int32 a = 1; } with a = 150 -> 08 96 01
        immutable Byte[] original = [0x08, 0x96, 0x01];
        AbstractMessage msg = new AbstractMessage();
        msg.mergeFromBytes(original);
        assert msg.toByteArray() == original;
    }

    @Test
    void shouldRoundTripSpecExample2() {
        // Test2 { string b = 2; } with b = "testing"
        immutable Byte[] original = [0x12, 0x07, 0x74, 0x65, 0x73, 0x74, 0x69, 0x6E, 0x67];
        AbstractMessage msg = new AbstractMessage();
        msg.mergeFromBytes(original);
        assert msg.toByteArray() == original;
    }

    @Test
    void shouldRoundTripSpecExample3() {
        // Test3 { Test1 c = 3; } with c.a = 150 -> 1a 03 08 96 01
        immutable Byte[] original = [0x1A, 0x03, 0x08, 0x96, 0x01];
        AbstractMessage msg = new AbstractMessage();
        msg.mergeFromBytes(original);
        assert msg.toByteArray() == original;
    }

    @Test
    void shouldRoundTripMixedWireTypes() {
        immutable Byte[] original = encode(o -> {
            o.writeVarint(42);      // not a field — let me use proper fields
        });
        // Use a proper multi-type message
        immutable Byte[] data = encode(o -> {
            o.writeInt32(1, 150);
            o.writeFixed32(2, 0x12345678);
            o.writeFixed64(3, 1);
            o.writeString(4, "test");
            o.writeBool(5, True);
        });
        AbstractMessage msg = new AbstractMessage();
        msg.mergeFromBytes(data);
        assert msg.toByteArray() == data;
    }

    @Test
    void shouldComputeCorrectSize() {
        immutable Byte[] data = encode(o -> {
            o.writeInt32(1, 150);
            o.writeString(2, "testing");
        });
        AbstractMessage msg = new AbstractMessage();
        msg.mergeFromBytes(data);
        assert msg.serializedSize() == data.size;
    }

    // ----- subclass: known + unknown fields ------------------------------------------------------

    @Test
    void shouldParseKnownFields() {
        immutable Byte[] data = encode(o -> {
            o.writeInt32(1, 42);
            o.writeString(2, "hello");
        });
        TestMessage msg = new TestMessage();
        msg.mergeFromBytes(data);
        assert msg.id   == 42;
        assert msg.name == "hello";
        assert msg.unknownFields.empty;
    }

    @Test
    void shouldPreserveUnknownFields() {
        immutable Byte[] data = encode(o -> {
            o.writeInt32(1, 42);
            o.writeString(2, "hello");
            o.writeFixed32(3, 0xDEADBEEF);  // unknown to TestMessage
            o.writeBool(4, True);             // unknown to TestMessage
        });
        TestMessage msg = new TestMessage();
        msg.mergeFromBytes(data);
        assert msg.id   == 42;
        assert msg.name == "hello";
        assert !msg.unknownFields.empty;
    }

    @Test
    void shouldRoundTripKnownAndUnknown() {
        immutable Byte[] data = encode(o -> {
            o.writeInt32(1, 42);
            o.writeString(2, "hello");
            o.writeFixed32(3, 0xDEADBEEF);
        });
        TestMessage msg = new TestMessage();
        msg.mergeFromBytes(data);

        // Re-serialize and re-parse
        immutable Byte[] result = msg.toByteArray();
        TestMessage msg2 = new TestMessage();
        msg2.mergeFromBytes(result);
        assert msg2.id   == 42;
        assert msg2.name == "hello";

        // The unknown field should also survive the round-trip
        // Parse as bare AbstractMessage to check field 3
        AbstractMessage raw = new AbstractMessage();
        raw.mergeFromBytes(result);
        // The result should contain all 3 fields
        assert raw.serializedSize() == data.size;
    }

    @Test
    void shouldSerializeKnownFieldsOnly() {
        TestMessage msg = new TestMessage();
        msg.id   = 42;
        msg.name = "hello";

        immutable Byte[] expected = encode(o -> {
            o.writeInt32(1, 42);
            o.writeString(2, "hello");
        });
        assert msg.toByteArray() == expected;
    }

    @Test
    void shouldComputeSizeWithKnownAndUnknown() {
        immutable Byte[] data = encode(o -> {
            o.writeInt32(1, 42);
            o.writeString(2, "hello");
            o.writeFixed32(3, 0xDEADBEEF);
        });
        TestMessage msg = new TestMessage();
        msg.mergeFromBytes(data);
        assert msg.serializedSize() == msg.toByteArray().size;
    }

    @Test
    void shouldHandleDefaultKnownFields() {
        // Known fields at default values should not be serialized
        TestMessage msg = new TestMessage();
        assert msg.serializedSize() == 0;
        assert msg.toByteArray().size == 0;
    }

    @Test
    void shouldMergeMultipleTimes() {
        TestMessage msg = new TestMessage();
        msg.mergeFromBytes(encode(o -> o.writeInt32(1, 10)));
        assert msg.id == 10;

        // Merge again — last value wins for known scalar fields
        msg.mergeFromBytes(encode(o -> o.writeInt32(1, 20)));
        assert msg.id == 20;
    }

    @Test
    void shouldHandleOnlyUnknownFields() {
        // All fields are unknown to TestMessage
        immutable Byte[] data = encode(o -> {
            o.writeFixed32(10, 0xAABBCCDD);
            o.writeFixed64(11, 123456789);
        });
        TestMessage msg = new TestMessage();
        msg.mergeFromBytes(data);
        assert msg.id   == 0;
        assert msg.name == "";
        assert !msg.unknownFields.empty;
        assert msg.toByteArray() == data;
    }

    // ----- helper --------------------------------------------------------------------------------

    private immutable Byte[] encode(function void(CodedOutput) writer) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        CodedOutput out = new CodedOutput(buf);
        writer(out);
        return buf.bytes.freeze(inPlace=True);
    }
}
