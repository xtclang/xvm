import ecstasy.io.ByteArrayInputStream;
import ecstasy.io.ByteArrayOutputStream;

import protobuf.CodedInput;
import protobuf.CodedOutput;
import protobuf.WireType;

class PackedFieldTest {

    // ----- packed varint round-trips -------------------------------------------------------------

    @Test
    void shouldRoundTripPackedVarints() {
        List<Int64> values = [1, 2, 3, 150, 300];
        immutable Byte[] bytes = encode(o -> o.writePackedVarints(1, values));

        CodedInput input = newInput(bytes);
        Int tag = input.readTag();
        assert WireType.getFieldNumber(tag) == 1;
        assert WireType.getWireType(tag) == WireType.LEN;

        List<Int64> result = input.readPackedVarints();
        assert result == values;
    }

    @Test
    void shouldRoundTripEmptyPackedVarints() {
        List<Int64> values = [];
        immutable Byte[] bytes = encode(o -> o.writePackedVarints(1, values));
        // Empty packed fields produce no output
        assert bytes.size == 0;
    }

    @Test
    void shouldRoundTripPackedVarintsSingleValue() {
        List<Int64> values = [42];
        immutable Byte[] bytes = encode(o -> o.writePackedVarints(1, values));

        CodedInput input = newInput(bytes);
        input.readTag();
        List<Int64> result = input.readPackedVarints();
        assert result == values;
    }

    @Test
    void shouldRoundTripPackedVarintsLargeValues() {
        List<Int64> values = [0, Int64.MaxValue, -1, Int64.MinValue];
        immutable Byte[] bytes = encode(o -> o.writePackedVarints(1, values));

        CodedInput input = newInput(bytes);
        input.readTag();
        List<Int64> result = input.readPackedVarints();
        assert result == values;
    }

    @Test
    void shouldComputePackedVarintsSize() {
        List<Int64> values = [1, 2, 3, 150, 300];
        immutable Byte[] bytes = encode(o -> o.writePackedVarints(1, values));
        assert CodedOutput.computePackedVarintsSize(1, values) == bytes.size;
    }

    // ----- packed int32 round-trips --------------------------------------------------------------

    @Test
    void shouldRoundTripPackedInt32s() {
        List<Int64> values = [1, -1, 127, -128, 0];
        immutable Byte[] bytes = encode(o -> o.writePackedVarints(1, values));

        CodedInput input = newInput(bytes);
        input.readTag();
        List<Int32> result = input.readPackedInt32s();
        assert result.size == values.size;
        for (Int i = 0; i < values.size; i++) {
            assert result[i] == values[i].toInt32();
        }
    }

    // ----- packed sint32 round-trips -------------------------------------------------------------

    @Test
    void shouldRoundTripPackedSInt32s() {
        List<Int32> values = [0, -1, 1, -2, 2, Int32.MaxValue, Int32.MinValue];
        immutable Byte[] bytes = encode(o -> o.writePackedSInt32s(1, values));

        CodedInput input = newInput(bytes);
        input.readTag();
        List<Int32> result = input.readPackedSInt32s();
        assert result == values;
    }

    @Test
    void shouldComputePackedSInt32sSize() {
        List<Int32> values = [0, -1, 1, -2, 2];
        immutable Byte[] bytes = encode(o -> o.writePackedSInt32s(1, values));
        assert CodedOutput.computePackedSInt32sSize(1, values) == bytes.size;
    }

    // ----- packed sint64 round-trips -------------------------------------------------------------

    @Test
    void shouldRoundTripPackedSInt64s() {
        List<Int64> values = [0, -1, 1, -2, 2, Int64.MaxValue, Int64.MinValue];
        immutable Byte[] bytes = encode(o -> o.writePackedSInt64s(1, values));

        CodedInput input = newInput(bytes);
        input.readTag();
        List<Int64> result = input.readPackedSInt64s();
        assert result == values;
    }

    @Test
    void shouldComputePackedSInt64sSize() {
        List<Int64> values = [0, -1, 1, -2, 2];
        immutable Byte[] bytes = encode(o -> o.writePackedSInt64s(1, values));
        assert CodedOutput.computePackedSInt64sSize(1, values) == bytes.size;
    }

    // ----- packed fixed32 round-trips ------------------------------------------------------------

    @Test
    void shouldRoundTripPackedFixed32s() {
        List<UInt32> values = [0, 1, 0xDEADBEEF, UInt32.MaxValue];
        immutable Byte[] bytes = encode(o -> o.writePackedFixed32s(1, values));

        CodedInput input = newInput(bytes);
        input.readTag();
        List<UInt32> result = input.readPackedFixed32s();
        assert result == values;
    }

    @Test
    void shouldComputePackedFixed32sSize() {
        List<UInt32> values = [1, 2, 3];
        immutable Byte[] bytes = encode(o -> o.writePackedFixed32s(1, values));
        assert CodedOutput.computePackedFixed32sSize(1, values) == bytes.size;
    }

    // ----- packed fixed64 round-trips ------------------------------------------------------------

    @Test
    void shouldRoundTripPackedFixed64s() {
        List<UInt64> values = [0, 1, UInt64.MaxValue, 0x123456789ABCDEF0];
        immutable Byte[] bytes = encode(o -> o.writePackedFixed64s(1, values));

        CodedInput input = newInput(bytes);
        input.readTag();
        List<UInt64> result = input.readPackedFixed64s();
        assert result == values;
    }

    @Test
    void shouldComputePackedFixed64sSize() {
        List<UInt64> values = [1, 2];
        immutable Byte[] bytes = encode(o -> o.writePackedFixed64s(1, values));
        assert CodedOutput.computePackedFixed64sSize(1, values) == bytes.size;
    }

    // ----- packed float round-trips --------------------------------------------------------------

    @Test
    void shouldRoundTripPackedFloats() {
        List<Float32> values = [0.0, 1.0, -1.0, 3.14];
        immutable Byte[] bytes = encode(o -> o.writePackedFloats(1, values));

        CodedInput input = newInput(bytes);
        input.readTag();
        List<Float32> result = input.readPackedFloats();
        assert result.size == values.size;
        for (Int i = 0; i < values.size; i++) {
            assert result[i] == values[i];
        }
    }

    @Test
    void shouldComputePackedFloatsSize() {
        List<Float32> values = [1.0, 2.0, 3.0];
        immutable Byte[] bytes = encode(o -> o.writePackedFloats(1, values));
        assert CodedOutput.computePackedFloatsSize(1, values) == bytes.size;
    }

    // ----- packed double round-trips -------------------------------------------------------------

    @Test
    void shouldRoundTripPackedDoubles() {
        List<Float64> values = [0.0, 1.0, -1.0, 3.141592653589793];
        immutable Byte[] bytes = encode(o -> o.writePackedDoubles(1, values));

        CodedInput input = newInput(bytes);
        input.readTag();
        List<Float64> result = input.readPackedDoubles();
        assert result.size == values.size;
        for (Int i = 0; i < values.size; i++) {
            assert result[i] == values[i];
        }
    }

    @Test
    void shouldComputePackedDoublesSize() {
        List<Float64> values = [1.0, 2.0];
        immutable Byte[] bytes = encode(o -> o.writePackedDoubles(1, values));
        assert CodedOutput.computePackedDoublesSize(1, values) == bytes.size;
    }

    // ----- packed bool round-trips ---------------------------------------------------------------

    @Test
    void shouldRoundTripPackedBools() {
        List<Boolean> values = [True, False, True, True, False];
        immutable Byte[] bytes = encode(o -> o.writePackedBools(1, values));

        CodedInput input = newInput(bytes);
        input.readTag();
        List<Boolean> result = input.readPackedBools();
        assert result == values;
    }

    @Test
    void shouldComputePackedBoolsSize() {
        List<Boolean> values = [True, False, True];
        immutable Byte[] bytes = encode(o -> o.writePackedBools(1, values));
        assert CodedOutput.computePackedBoolsSize(1, values) == bytes.size;
    }

    // ----- packed fields with other fields -------------------------------------------------------

    @Test
    void shouldRoundTripPackedWithOtherFields() {
        immutable Byte[] bytes = encode(o -> {
            o.writeInt32(1, 42);
            o.writePackedVarints(2, [10, 20, 30]);
            o.writeString(3, "hello");
        });

        CodedInput input = newInput(bytes);

        // Field 1: int32
        Int tag1 = input.readTag();
        assert WireType.getFieldNumber(tag1) == 1;
        assert input.readInt32() == 42;

        // Field 2: packed varints
        Int tag2 = input.readTag();
        assert WireType.getFieldNumber(tag2) == 2;
        assert WireType.getWireType(tag2) == WireType.LEN;
        List<Int64> packed = input.readPackedVarints();
        assert packed == [10, 20, 30];

        // Field 3: string
        Int tag3 = input.readTag();
        assert WireType.getFieldNumber(tag3) == 3;
        assert input.readString() == "hello";
    }

    @Test
    void shouldComputeCorrectTotalSize() {
        List<Int64>  varints  = [10, 20, 30];
        List<UInt32> fixed32s = [1, 2];

        immutable Byte[] bytes = encode(o -> {
            o.writeInt32(1, 42);
            o.writePackedVarints(2, varints);
            o.writePackedFixed32s(3, fixed32s);
        });

        Int expectedSize = CodedOutput.computeInt32Size(1, 42)
                         + CodedOutput.computePackedVarintsSize(2, varints)
                         + CodedOutput.computePackedFixed32sSize(3, fixed32s);
        assert expectedSize == bytes.size;
    }

    // ----- wire format verification --------------------------------------------------------------

    @Test
    void shouldProduceCorrectPackedVarintBytes() {
        // Field 1, packed varints [3, 270, 86942]
        // From protobuf encoding docs:
        //   tag: 0x0A (field 1, wire type LEN=2)
        //   length: 0x06 (6 bytes)
        //   value 3:     0x03
        //   value 270:   0x8E 0x02
        //   value 86942: 0x9E 0xA7 0x05
        immutable Byte[] bytes = encode(o -> o.writePackedVarints(1, [3, 270, 86942]));
        assert bytes == [0x0A, 0x06, 0x03, 0x8E, 0x02, 0x9E, 0xA7, 0x05];
    }

    // ----- packed in AbstractMessage -------------------------------------------------------------

    @Test
    void shouldRoundTripPackedInAbstractMessage() {
        import protobuf.AbstractMessage;

        // Encode a message with a packed field (field 2, LEN wire type)
        immutable Byte[] bytes = encode(o -> {
            o.writeInt32(1, 42);
            o.writePackedVarints(2, [10, 20, 30]);
        });

        // AbstractMessage stores packed fields as unknown LEN fields
        AbstractMessage msg = new AbstractMessage();
        msg.mergeFrom(newInput(bytes));

        // Round-trip: unknown fields should reproduce the same bytes
        assert msg.toByteArray() == bytes;
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
