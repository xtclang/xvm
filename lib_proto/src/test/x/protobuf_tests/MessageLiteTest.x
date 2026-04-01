import ecstasy.io.ByteArrayInputStream;
import ecstasy.io.ByteArrayOutputStream;

import protobuf.CodedInput;
import protobuf.CodedOutput;
import protobuf.MessageLite;
import protobuf.WireType;

class MessageLiteTest {

    /**
     * A simple test message equivalent to:
     * ```proto
     * message Test1 {
     *   int32 a = 1;
     * }
     * ```
     */
    static class Test1
            implements MessageLite {

        Int32 a = 0;

        @Override
        void writeTo(CodedOutput out) {
            if (a != 0) {
                out.writeInt32(1, a);
            }
        }

        @Override
        MessageLite mergeFrom(CodedInput input) {
            while (!input.isAtEnd()) {
                Int      tag         = input.readTag();
                Int      fieldNumber = WireType.getFieldNumber(tag);
                WireType wireType    = WireType.getWireType(tag);
                switch (fieldNumber) {
                case 1:
                    a = input.readInt32();
                    break;
                default:
                    input.skipField(wireType);
                    break;
                }
            }
            return this;
        }

        @Override
        Int serializedSize() {
            Int size = 0;
            if (a != 0) {
                size += CodedOutput.computeInt32Size(1, a);
            }
            return size;
        }

        @Override
        immutable Test1 freeze(Boolean inPlace = False) {
            return this.is(immutable Test1) ? this : this.makeImmutable();
        }
    }

    /**
     * A multi-field test message equivalent to:
     * ```proto
     * message TestMulti {
     *   int32  id   = 1;
     *   string name = 2;
     *   bool   flag = 3;
     * }
     * ```
     */
    static class TestMulti
            implements MessageLite {

        Int32   id   = 0;
        String  name = "";
        Boolean flag = False;

        @Override
        void writeTo(CodedOutput out) {
            if (id != 0) {
                out.writeInt32(1, id);
            }
            if (name.size > 0) {
                out.writeString(2, name);
            }
            if (flag) {
                out.writeBool(3, flag);
            }
        }

        @Override
        MessageLite mergeFrom(CodedInput input) {
            while (!input.isAtEnd()) {
                Int      tag         = input.readTag();
                Int      fieldNumber = WireType.getFieldNumber(tag);
                WireType wireType    = WireType.getWireType(tag);
                switch (fieldNumber) {
                case 1:
                    id = input.readInt32();
                    break;
                case 2:
                    name = input.readString();
                    break;
                case 3:
                    flag = input.readBool();
                    break;
                default:
                    input.skipField(wireType);
                    break;
                }
            }
            return this;
        }

        @Override
        Int serializedSize() {
            Int size = 0;
            if (id != 0) {
                size += CodedOutput.computeInt32Size(1, id);
            }
            if (name.size > 0) {
                size += CodedOutput.computeStringSize(2, name);
            }
            if (flag) {
                size += CodedOutput.computeBoolSize(3);
            }
            return size;
        }

        @Override
        immutable TestMulti freeze(Boolean inPlace = False) {
            return this.is(immutable TestMulti) ? this : this.makeImmutable();
        }
    }

    /**
     * A message with an embedded sub-message equivalent to:
     * ```proto
     * message TestNested {
     *   int32 id    = 1;
     *   Test1 child = 2;
     * }
     * ```
     */
    static class TestNested
            implements MessageLite {

        Int32  id    = 0;
        Test1? child = Null;

        @Override
        void writeTo(CodedOutput out) {
            if (id != 0) {
                out.writeInt32(1, id);
            }
            if (Test1 c := child.is(Test1)) {
                out.writeMessage(2, c);
            }
        }

        @Override
        MessageLite mergeFrom(CodedInput input) {
            while (!input.isAtEnd()) {
                Int      tag         = input.readTag();
                Int      fieldNumber = WireType.getFieldNumber(tag);
                WireType wireType    = WireType.getWireType(tag);
                switch (fieldNumber) {
                case 1:
                    id = input.readInt32();
                    break;
                case 2:
                    Int length   = input.readVarint().toInt();
                    Int oldLimit = input.pushLimit(length);
                    if (child == Null) {
                        child = new Test1();
                    }
                    child?.mergeFrom(input);
                    input.popLimit(oldLimit);
                    break;
                default:
                    input.skipField(wireType);
                    break;
                }
            }
            return this;
        }

        @Override
        Int serializedSize() {
            Int size = 0;
            if (id != 0) {
                size += CodedOutput.computeInt32Size(1, id);
            }
            if (Test1 c := child.is(Test1)) {
                size += CodedOutput.computeMessageSize(2, c);
            }
            return size;
        }

        @Override
        immutable TestNested freeze(Boolean inPlace = False) {
            return this.is(immutable TestNested) ? this : this.makeImmutable();
        }
    }

    // ----- Test1 tests -------------------------------------------------------------------

    @Test
    void shouldSerializeSimpleMessage() {
        Test1 msg = new Test1();
        msg.a = 150;
        immutable Byte[] bytes = msg.toByteArray();
        // From protobuf spec: field 1, int32=150 -> 08 96 01
        assert bytes == [0x08, 0x96, 0x01];
    }

    @Test
    void shouldDeserializeSimpleMessage() {
        Test1 msg = new Test1();
        msg.mergeFromBytes([0x08, 0x96, 0x01]);
        assert msg.a == 150;
    }

    @Test
    void shouldRoundTripSimpleMessage() {
        Test1 original = new Test1();
        original.a = 42;
        immutable Byte[] bytes = original.toByteArray();

        Test1 restored = new Test1();
        restored.mergeFromBytes(bytes);
        assert restored.a == 42;
    }

    @Test
    void shouldSerializeDefaultMessage() {
        Test1 msg = new Test1();
        immutable Byte[] bytes = msg.toByteArray();
        assert bytes.size == 0;
    }

    @Test
    void shouldComputeCorrectSize() {
        Test1 msg = new Test1();
        msg.a = 150;
        assert msg.serializedSize() == 3;
        assert msg.toByteArray().size == msg.serializedSize();
    }

    // ----- TestMulti tests ---------------------------------------------------------------

    @Test
    void shouldRoundTripMultiFieldMessage() {
        TestMulti original = new TestMulti();
        original.id   = 42;
        original.name = "hello";
        original.flag = True;
        immutable Byte[] bytes = original.toByteArray();

        TestMulti restored = new TestMulti();
        restored.mergeFromBytes(bytes);
        assert restored.id   == 42;
        assert restored.name == "hello";
        assert restored.flag == True;
    }

    @Test
    void shouldHandlePartialFields() {
        // Only serialize id, leave name and flag as defaults
        TestMulti original = new TestMulti();
        original.id = 99;
        immutable Byte[] bytes = original.toByteArray();

        TestMulti restored = new TestMulti();
        restored.mergeFromBytes(bytes);
        assert restored.id   == 99;
        assert restored.name == "";
        assert restored.flag == False;
    }

    @Test
    void shouldMergeOverwriteScalars() {
        // First set of data
        TestMulti msg = new TestMulti();
        msg.mergeFromBytes(encode(o -> {
            o.writeInt32(1, 10);
            o.writeString(2, "first");
        }));
        assert msg.id   == 10;
        assert msg.name == "first";

        // Merge again — last value wins for scalars
        msg.mergeFromBytes(encode(o -> {
            o.writeInt32(1, 20);
            o.writeString(2, "second");
        }));
        assert msg.id   == 20;
        assert msg.name == "second";
    }

    @Test
    void shouldSkipUnknownFields() {
        // Write fields 1, 4 (unknown), and 2
        immutable Byte[] bytes = encode(o -> {
            o.writeInt32(1, 42);
            o.writeFixed32(4, 0xDEADBEEF);  // unknown field
            o.writeString(2, "hello");
        });

        TestMulti msg = new TestMulti();
        msg.mergeFromBytes(bytes);
        assert msg.id   == 42;
        assert msg.name == "hello";
    }

    @Test
    void shouldComputeMultiFieldSize() {
        TestMulti msg = new TestMulti();
        msg.id   = 1;
        msg.name = "hi";
        msg.flag = True;
        assert msg.toByteArray().size == msg.serializedSize();
    }

    // ----- TestNested tests --------------------------------------------------------------

    @Test
    void shouldRoundTripNestedMessage() {
        TestNested original = new TestNested();
        original.id    = 1;
        original.child = new Test1();
        original.child?.a = 150;
        immutable Byte[] bytes = original.toByteArray();

        TestNested restored = new TestNested();
        restored.mergeFromBytes(bytes);
        assert restored.id == 1;
        assert restored.child != Null;
        assert restored.child?.a == 150;
    }

    @Test
    void shouldDeserializeNestedFromSpec() {
        // From protobuf spec: Test3 { Test1 c = 3; } with c.a = 150
        // We use field 2 for child, so re-encode: field 1 (id=1), field 2 (child with a=150)
        immutable Byte[] bytes = encode(o -> {
            o.writeInt32(1, 1);
            // sub-message: field 1, int32=150 -> 08 96 01
            o.writeBytes(2, [0x08, 0x96, 0x01]);
        });

        TestNested msg = new TestNested();
        msg.mergeFromBytes(bytes);
        assert msg.id == 1;
        assert msg.child != Null;
        assert msg.child?.a == 150;
    }

    @Test
    void shouldComputeNestedSize() {
        TestNested msg = new TestNested();
        msg.id    = 1;
        msg.child = new Test1();
        msg.child?.a = 150;
        assert msg.toByteArray().size == msg.serializedSize();
    }

    // ----- writeMessage tests -----------------------------------------------------------

    @Test
    void shouldWriteMessageDirectly() {
        // Use writeMessage to serialize a sub-message with automatic length prefix
        Test1 child = new Test1();
        child.a = 150;

        immutable Byte[] bytes = encode(o -> {
            o.writeInt32(1, 1);
            o.writeMessage(2, child);
        });

        // Should produce the same bytes as manually serializing
        TestNested manual = new TestNested();
        manual.id    = 1;
        manual.child = child;
        assert bytes == manual.toByteArray();
    }

    @Test
    void shouldRoundTripWithWriteMessage() {
        Test1 child = new Test1();
        child.a = 42;

        immutable Byte[] bytes = encode(o -> o.writeMessage(1, child));

        // Read back the sub-message
        CodedInput input = new CodedInput(new ByteArrayInputStream(bytes));
        Int tag = input.readTag();
        assert WireType.getWireType(tag) == WireType.LEN;

        Int length   = input.readVarint().toInt();
        Int oldLimit = input.pushLimit(length);

        Test1 restored = new Test1();
        restored.mergeFrom(input);
        assert restored.a == 42;

        input.popLimit(oldLimit);
        assert input.isAtEnd();
    }

    // ----- size computation tests --------------------------------------------------------

    @Test
    void shouldComputeFieldSizesCorrectly() {
        // int32 field 1, value 150: tag(1 byte) + varint 150(2 bytes) = 3
        assert CodedOutput.computeInt32Size(1, 150) == 3;

        // bool field 1: tag(1 byte) + 1 byte = 2
        assert CodedOutput.computeBoolSize(1) == 2;

        // fixed32 field 1: tag(1 byte) + 4 bytes = 5
        assert CodedOutput.computeFixed32Size(1) == 5;

        // fixed64 field 1: tag(1 byte) + 8 bytes = 9
        assert CodedOutput.computeFixed64Size(1) == 9;

        // string field 2, "testing": tag(1 byte) + length varint(1 byte) + 7 bytes = 9
        assert CodedOutput.computeStringSize(2, "testing") == 9;

        // bytes field 1, 3 bytes: tag(1 byte) + length varint(1 byte) + 3 bytes = 5
        assert CodedOutput.computeBytesSize(1, [0xAA, 0xBB, 0xCC]) == 5;
    }

    @Test
    void shouldComputeMessageSizeCorrectly() {
        Test1 child = new Test1();
        child.a = 150;
        // child serialized = 3 bytes (08 96 01)
        // message field 2: tag(1 byte) + length varint(1 byte) + 3 bytes = 5
        assert CodedOutput.computeMessageSize(2, child) == 5;
    }

    // ----- helper ------------------------------------------------------------------------

    private immutable Byte[] encode(function void(CodedOutput) writer) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        CodedOutput out = new CodedOutput(buf);
        writer(out);
        return buf.bytes.freeze(inPlace=True);
    }
}
