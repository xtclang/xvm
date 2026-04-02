import ecstasy.io.ByteArrayInputStream;
import ecstasy.io.ByteArrayOutputStream;

import protobuf.CodedInput;
import protobuf.CodedOutput;
import protobuf.FieldValue;
import protobuf.FieldValue.Fixed32Value;
import protobuf.FieldValue.Fixed64Value;
import protobuf.FieldValue.LengthValue;
import protobuf.FieldValue.VarintValue;
import protobuf.GenericMessage;
import protobuf.WireType;

class GenericMessageTest {

    // ----- basic field access --------------------------------------------------------------------

    @Test
    void shouldSetAndGetVarint() {
        GenericMessage msg = new GenericMessage();
        msg.setVarint(1, 150);
        assert msg.getVarint(1) == 150;
    }

    @Test
    void shouldSetAndGetString() {
        GenericMessage msg = new GenericMessage();
        msg.setString(2, "hello");
        assert msg.getString(2) == "hello";
    }

    @Test
    void shouldSetAndGetBytes() {
        GenericMessage msg = new GenericMessage();
        Byte[] data = [0xDE, 0xAD, 0xBE, 0xEF];
        msg.setBytes(3, data);
        assert msg.getBytes(3) == data;
    }

    @Test
    void shouldSetAndGetFixed32() {
        GenericMessage msg = new GenericMessage();
        msg.setFixed32(4, 0x12345678);
        assert msg.getFixed32(4) == 0x12345678;
    }

    @Test
    void shouldSetAndGetFixed64() {
        GenericMessage msg = new GenericMessage();
        msg.setFixed64(5, 0x123456789ABCDEF0);
        assert msg.getFixed64(5) == 0x123456789ABCDEF0;
    }

    @Test
    void shouldReportHasField() {
        GenericMessage msg = new GenericMessage();
        assert !msg.hasField(1);
        msg.setVarint(1, 42);
        assert msg.hasField(1);
    }

    @Test
    void shouldClearField() {
        GenericMessage msg = new GenericMessage();
        msg.setVarint(1, 42);
        assert msg.hasField(1);
        msg.clearField(1);
        assert !msg.hasField(1);
    }

    // ----- overwrite semantics -------------------------------------------------------------------

    @Test
    void shouldOverwriteOnSet() {
        GenericMessage msg = new GenericMessage();
        msg.setVarint(1, 10);
        msg.setVarint(1, 20);
        assert msg.getVarint(1) == 20;
        // set replaces, so only one value
        assert msg.getFieldValues(1).size == 1;
    }

    // ----- repeated fields -----------------------------------------------------------------------

    @Test
    void shouldAddRepeatedVarint() {
        GenericMessage msg = new GenericMessage();
        msg.addVarint(1, 10);
        msg.addVarint(1, 20);
        msg.addVarint(1, 30);
        List<Int64> values = msg.getRepeatedVarint(1);
        assert values.size == 3;
        assert values[0] == 10;
        assert values[1] == 20;
        assert values[2] == 30;
    }

    @Test
    void shouldGetLastVarintForRepeatedField() {
        GenericMessage msg = new GenericMessage();
        msg.addVarint(1, 10);
        msg.addVarint(1, 20);
        // getVarint returns the last value (last-one-wins)
        assert msg.getVarint(1) == 20;
    }

    // ----- sub-messages --------------------------------------------------------------------------

    @Test
    void shouldSetAndGetSubMessage() {
        GenericMessage child = new GenericMessage();
        child.setVarint(1, 150);

        GenericMessage parent = new GenericMessage();
        parent.setMessage(2, child);

        GenericMessage restored = parent.getMessage(2);
        assert restored.getVarint(1) == 150;
    }

    // ----- serialization: protobuf spec examples -------------------------------------------------

    @Test
    void shouldSerializeSpecExample1() {
        // Test1 { int32 a = 1; } with a = 150 -> 08 96 01
        GenericMessage msg = new GenericMessage();
        msg.setVarint(1, 150);
        immutable Byte[] bytes = msg.toByteArray();
        assert bytes == [0x08, 0x96, 0x01];
    }

    @Test
    void shouldSerializeSpecExample2() {
        // Test2 { string b = 2; } with b = "testing" -> 12 07 74 65 73 74 69 6e 67
        GenericMessage msg = new GenericMessage();
        msg.setString(2, "testing");
        immutable Byte[] bytes = msg.toByteArray();
        assert bytes == [0x12, 0x07, 0x74, 0x65, 0x73, 0x74, 0x69, 0x6E, 0x67];
    }

    @Test
    void shouldSerializeSpecExample3() {
        // Test3 { Test1 c = 3; } with c.a = 150 -> 1a 03 08 96 01
        GenericMessage child = new GenericMessage();
        child.setVarint(1, 150);

        GenericMessage msg = new GenericMessage();
        msg.setMessage(3, child);
        immutable Byte[] bytes = msg.toByteArray();
        assert bytes == [0x1A, 0x03, 0x08, 0x96, 0x01];
    }

    // ----- deserialization -----------------------------------------------------------------------

    @Test
    void shouldDeserializeSpecExample1() {
        // 08 96 01 -> field 1, int32 = 150
        GenericMessage msg = new GenericMessage();
        msg.mergeFromBytes([0x08, 0x96, 0x01]);
        assert msg.getVarint(1) == 150;
    }

    @Test
    void shouldDeserializeSpecExample2() {
        // 12 07 74 65 73 74 69 6e 67 -> field 2, string = "testing"
        GenericMessage msg = new GenericMessage();
        msg.mergeFromBytes([0x12, 0x07, 0x74, 0x65, 0x73, 0x74, 0x69, 0x6E, 0x67]);
        assert msg.getString(2) == "testing";
    }

    @Test
    void shouldDeserializeSpecExample3() {
        // 1a 03 08 96 01 -> field 3, embedded message with field 1 = 150
        GenericMessage msg = new GenericMessage();
        msg.mergeFromBytes([0x1A, 0x03, 0x08, 0x96, 0x01]);
        GenericMessage child = msg.getMessage(3);
        assert child.getVarint(1) == 150;
    }

    // ----- round-trip tests ----------------------------------------------------------------------

    @Test
    void shouldRoundTripSimpleMessage() {
        GenericMessage original = new GenericMessage();
        original.setVarint(1, 42);
        original.setString(2, "hello");
        original.setFixed32(3, 0xDEADBEEF);

        immutable Byte[] bytes = original.toByteArray();

        GenericMessage restored = new GenericMessage();
        restored.mergeFromBytes(bytes);
        assert restored.getVarint(1) == 42;
        assert restored.getString(2) == "hello";
        assert restored.getFixed32(3) == 0xDEADBEEF;
    }

    @Test
    void shouldRoundTripNestedMessage() {
        GenericMessage child = new GenericMessage();
        child.setVarint(1, 99);
        child.setString(2, "nested");

        GenericMessage original = new GenericMessage();
        original.setVarint(1, 1);
        original.setMessage(2, child);

        immutable Byte[] bytes = original.toByteArray();

        GenericMessage restored = new GenericMessage();
        restored.mergeFromBytes(bytes);
        assert restored.getVarint(1) == 1;

        GenericMessage restoredChild = restored.getMessage(2);
        assert restoredChild.getVarint(1) == 99;
        assert restoredChild.getString(2) == "nested";
    }

    @Test
    void shouldRoundTripMultipleFieldTypes() {
        GenericMessage original = new GenericMessage();
        original.setVarint(1, -1);
        original.setFixed32(2, 42);
        original.setFixed64(3, 123456789);
        original.setString(4, "test");
        original.setBytes(5, [0x01, 0x02, 0x03]);

        immutable Byte[] bytes = original.toByteArray();

        GenericMessage restored = new GenericMessage();
        restored.mergeFromBytes(bytes);
        assert restored.getVarint(1)  == -1;
        assert restored.getFixed32(2) == 42;
        assert restored.getFixed64(3) == 123456789;
        assert restored.getString(4)  == "test";
        assert restored.getBytes(5)   == [0x01, 0x02, 0x03];
    }

    @Test
    void shouldRoundTripRepeatedVarint() {
        GenericMessage original = new GenericMessage();
        original.addVarint(1, 10);
        original.addVarint(1, 20);
        original.addVarint(1, 30);

        immutable Byte[] bytes = original.toByteArray();

        GenericMessage restored = new GenericMessage();
        restored.mergeFromBytes(bytes);
        List<Int64> values = restored.getRepeatedVarint(1);
        assert values.size == 3;
        assert values[0] == 10;
        assert values[1] == 20;
        assert values[2] == 30;
    }

    // ----- size computation ----------------------------------------------------------------------

    @Test
    void shouldComputeCorrectSize() {
        GenericMessage msg = new GenericMessage();
        msg.setVarint(1, 150);
        // tag(1 byte) + varint 150(2 bytes) = 3
        assert msg.serializedSize() == 3;
        assert msg.toByteArray().size == msg.serializedSize();
    }

    @Test
    void shouldComputeCorrectSizeMultiField() {
        GenericMessage msg = new GenericMessage();
        msg.setVarint(1, 150);
        msg.setString(2, "testing");
        Int size = msg.serializedSize();
        assert msg.toByteArray().size == size;
    }

    // ----- merge semantics -----------------------------------------------------------------------

    @Test
    void shouldMergeAppendToExistingFields() {
        GenericMessage msg = new GenericMessage();
        msg.mergeFromBytes([0x08, 0x0A]);  // field 1, varint 10
        msg.mergeFromBytes([0x08, 0x14]);  // field 1, varint 20
        // Both values should be stored
        List<Int64> values = msg.getRepeatedVarint(1);
        assert values.size == 2;
        assert values[0] == 10;
        assert values[1] == 20;
        // Last-one-wins for scalar access
        assert msg.getVarint(1) == 20;
    }

    @Test
    void shouldPreserveFieldOrder() {
        GenericMessage msg = new GenericMessage();
        msg.setVarint(3, 30);
        msg.setString(1, "first");
        msg.setFixed32(2, 20);

        // ListMap preserves insertion order
        immutable Byte[] bytes = msg.toByteArray();

        // Re-parse and verify the field order by checking the tag sequence
        CodedInput input = new CodedInput(new ByteArrayInputStream(bytes));
        Int tag1 = input.readTag();
        assert WireType.getFieldNumber(tag1) == 3;
    }

    // ----- empty message -------------------------------------------------------------------------

    @Test
    void shouldHandleEmptyMessage() {
        GenericMessage msg = new GenericMessage();
        assert msg.serializedSize() == 0;
        assert msg.toByteArray().size == 0;

        GenericMessage restored = new GenericMessage();
        restored.mergeFromBytes([]);
        assert !restored.hasField(1);
    }
}
