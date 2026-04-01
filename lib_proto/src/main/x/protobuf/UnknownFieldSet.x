import ecstasy.maps.ListMap;

import FieldValue.Fixed32Value;
import FieldValue.Fixed64Value;
import FieldValue.LengthValue;
import FieldValue.VarintValue;

/**
 * A container for unknown protobuf fields encountered during deserialization.
 *
 * Unknown fields are stored by field number and can be replayed during serialization,
 * preserving the original wire format. A [ListMap] is used to preserve field insertion order.
 */
class UnknownFieldSet
        implements Freezable, Duplicable {

    /**
     * Creates a new, empty unknown field set.
     */
    construct() {
        fields = new ListMap();
    }

    /**
     * Creates a new unknown field set that is a copy of the given one.
     */
    @Override
    construct(UnknownFieldSet other) {
        if (other.is(immutable UnknownFieldSet)) {
            fields = other.fields;
        } else {
            fields = new ListMap();
            fields.putAll(other.fields);
        }
    }

    /**
     * The stored unknown fields, keyed by field number.
     */
    private ListMap<Int, List<FieldValue>> fields;

    /**
     * @return True if there are no unknown fields stored
     */
    Boolean empty.get() {
        return fields.empty;
    }

    /**
     * @return the number of distinct field numbers stored
     */
    Int size.get() {
        return fields.size;
    }

    /**
     * Read a single field from the input and store it. The tag has already been read
     * by the caller.
     *
     * @param input  the coded input to read from
     * @param tag    the tag that was already read (contains field number and wire type)
     */
    void mergeFieldFrom(CodedInput input, Int tag) {
        Int      fieldNumber = WireType.getFieldNumber(tag);
        WireType wireType    = WireType.getWireType(tag);

        switch (wireType) {
        case VARINT:
            addField(fieldNumber, new VarintValue(input.readVarint()));
            break;

        case I32:
            addField(fieldNumber, new Fixed32Value(input.readFixed32()));
            break;

        case I64:
            addField(fieldNumber, new Fixed64Value(input.readFixed64()));
            break;

        case LEN:
            addField(fieldNumber, new LengthValue(input.readBytes()));
            break;

        case SGROUP:
        case EGROUP:
            // Groups are deprecated; skip them
            input.skipField(wireType);
            break;
        }
    }

    /**
     * Write all stored unknown fields to the given output.
     *
     * @param out  the coded output to write to
     */
    void writeTo(CodedOutput out) {
        for ((Int fieldNumber, List<FieldValue> values) : fields) {
            for (FieldValue value : values) {
                out.writeTag(fieldNumber, value.wireType);
                value.writeTo(out);
            }
        }
    }

    /**
     * Compute the total serialized size of all stored unknown fields.
     *
     * @return the number of bytes required
     */
    Int serializedSize() {
        Int size = 0;
        for ((Int fieldNumber, List<FieldValue> values) : fields) {
            for (FieldValue value : values) {
                size += CodedOutput.computeTagSize(fieldNumber) + value.wireSize;
            }
        }
        return size;
    }

    /**
     * Remove all stored unknown fields.
     */
    void clear() {
        fields.clear();
    }

    /**
     * Add a field value for the given field number.
     */
    private void addField(Int fieldNumber, FieldValue value) {
        if (List<FieldValue> values := fields.get(fieldNumber)) {
            values.add(value);
        } else {
            fields.put(fieldNumber, new Array<FieldValue>().add(value));
        }
    }

    @Override
    immutable UnknownFieldSet freeze(Boolean inPlace = False) {
        if (this.is(immutable UnknownFieldSet)) {
            return this;
        }

        if (inPlace) {
            fields.makeImmutable();
            return this.makeImmutable();
        }
        UnknownFieldSet frozen = new UnknownFieldSet();
        frozen.fields.putAll(fields);
        frozen.fields.makeImmutable();
        return frozen.makeImmutable();
    }
}
