import ecstasy.io.ByteArrayInputStream;
import ecstasy.io.ByteArrayOutputStream;

import protobuf.CodedInput;
import protobuf.CodedOutput;
import protobuf.ProtoEnum;
import protobuf.WireType;

class ProtoEnumTest {

    /**
     * A test enum with sequential values starting at 0.
     */
    enum Color
            implements ProtoEnum {
        Red(0), Green(1), Blue(2);

        construct(Int protoValue) {
            this.protoValue = protoValue;
        }

        @Override
        Int protoValue;
    }

    /**
     * A test enum with sparse/non-sequential values.
     */
    enum Status
            implements ProtoEnum {
        Unknown(0), Active(1), Inactive(2), Deleted(99);

        construct(Int protoValue) {
            this.protoValue = protoValue;
        }

        @Override
        Int protoValue;
    }

    // ----- ProtoEnum interface ---------------------------------------------------------------

    @Test
    void shouldReturnProtoValue() {
        assert Color.Red.protoValue   == 0;
        assert Color.Green.protoValue == 1;
        assert Color.Blue.protoValue  == 2;
    }

    @Test
    void shouldReturnSparseProtoValue() {
        assert Status.Unknown.protoValue  == 0;
        assert Status.Active.protoValue   == 1;
        assert Status.Inactive.protoValue == 2;
        assert Status.Deleted.protoValue  == 99;
    }

    @Test
    void shouldLookUpByProtoValue() {
        assert Color c := ProtoEnum.byProtoValue(Color.values, 1);
        assert c == Color.Green;
    }

    @Test
    void shouldLookUpSparseByProtoValue() {
        assert Status s := ProtoEnum.byProtoValue(Status.values, 99);
        assert s == Status.Deleted;
    }

    @Test
    void shouldReturnFalseForUnknownProtoValue() {
        assert !ProtoEnum.byProtoValue(Color.values, 42);
    }

    @Test
    void shouldLookUpByZeroProtoValue() {
        assert Color c := ProtoEnum.byProtoValue(Color.values, 0);
        assert c == Color.Red;
    }

    // ----- write/read round-trips ------------------------------------------------------------

    @Test
    void shouldRoundTripEnumField() {
        immutable Byte[] bytes = encode(o -> o.writeEnum(1, Color.Blue));

        CodedInput input = newInput(bytes);
        Int tag = input.readTag();
        assert WireType.getFieldNumber(tag) == 1;
        assert WireType.getWireType(tag) == WireType.VARINT;

        Int32 raw = input.readEnum();
        assert Color c := ProtoEnum.byProtoValue(Color.values, raw);
        assert c == Color.Blue;
    }

    @Test
    void shouldRoundTripSparseEnumField() {
        immutable Byte[] bytes = encode(o -> o.writeEnum(1, Status.Deleted));

        CodedInput input = newInput(bytes);
        input.readTag();
        Int32 raw = input.readEnum();
        assert Status s := ProtoEnum.byProtoValue(Status.values, raw);
        assert s == Status.Deleted;
    }

    @Test
    void shouldRoundTripEnumValueField() {
        // Write using raw int value
        immutable Byte[] bytes = encode(o -> o.writeEnumValue(1, 2));

        CodedInput input = newInput(bytes);
        input.readTag();
        Int32 raw = input.readEnum();
        assert Color c := ProtoEnum.byProtoValue(Color.values, raw);
        assert c == Color.Blue;
    }

    @Test
    void shouldRoundTripZeroEnum() {
        immutable Byte[] bytes = encode(o -> o.writeEnum(1, Status.Unknown));

        CodedInput input = newInput(bytes);
        input.readTag();
        Int32 raw = input.readEnum();
        assert Status s := ProtoEnum.byProtoValue(Status.values, raw);
        assert s == Status.Unknown;
    }

    // ----- size computation ------------------------------------------------------------------

    @Test
    void shouldComputeEnumSize() {
        immutable Byte[] bytes = encode(o -> o.writeEnum(1, Color.Green));
        assert CodedOutput.computeEnumSize(1, Color.Green) == bytes.size;
    }

    @Test
    void shouldComputeSparseEnumSize() {
        immutable Byte[] bytes = encode(o -> o.writeEnum(1, Status.Deleted));
        assert CodedOutput.computeEnumSize(1, Status.Deleted) == bytes.size;
    }

    @Test
    void shouldComputeEnumValueSize() {
        immutable Byte[] bytes = encode(o -> o.writeEnumValue(1, 99));
        assert CodedOutput.computeEnumValueSize(1, 99) == bytes.size;
    }

    // ----- enum in a message -----------------------------------------------------------------

    @Test
    void shouldRoundTripEnumWithOtherFields() {
        immutable Byte[] bytes = encode(o -> {
            o.writeString(1, "test");
            o.writeEnum(2, Status.Active);
            o.writeInt32(3, 42);
        });

        CodedInput input = newInput(bytes);

        input.readTag();
        assert input.readString() == "test";

        input.readTag();
        Int32 raw = input.readEnum();
        assert Status s := ProtoEnum.byProtoValue(Status.values, raw);
        assert s == Status.Active;

        input.readTag();
        assert input.readInt32() == 42;
    }

    @Test
    void shouldPreserveUnrecognizedEnumValue() {
        // Write a value that isn't in our enum (open enum / proto3 behavior)
        immutable Byte[] bytes = encode(o -> o.writeEnumValue(1, 999));

        CodedInput input = newInput(bytes);
        input.readTag();
        Int32 raw = input.readEnum();
        assert raw == 999;
        // Lookup should fail for unrecognized value
        assert !ProtoEnum.byProtoValue(Status.values, raw);
    }

    // ----- wire format verification ----------------------------------------------------------

    @Test
    void shouldProduceCorrectEnumBytes() {
        // Enum is just a varint — Color.Blue (value=2) on field 1:
        // tag(1, VARINT) = 0x08, value 2 = 0x02
        immutable Byte[] bytes = encode(o -> o.writeEnum(1, Color.Blue));
        assert bytes == [0x08, 0x02];
    }

    @Test
    void shouldProduceSameAsInt32() {
        // writeEnum should produce identical bytes to writeInt32
        immutable Byte[] enumBytes = encode(o -> o.writeEnum(1, Status.Deleted));
        immutable Byte[] intBytes  = encode(o -> o.writeInt32(1, 99));
        assert enumBytes == intBytes;
    }

    // ----- helper ----------------------------------------------------------------------------

    private immutable Byte[] encode(function void(CodedOutput) writer) {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        CodedOutput out = new CodedOutput(buf);
        writer(out);
        return buf.bytes.freeze(inPlace=True);
    }

    private CodedInput newInput(Byte[] bytes) {
        return new CodedInput(new ByteArrayInputStream(bytes));
    }
}
