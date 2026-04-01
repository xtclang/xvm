import ecstasy.maps.ListMap;

import FieldValue.Fixed32Value;
import FieldValue.Fixed64Value;
import FieldValue.LengthValue;
import FieldValue.VarintValue;

/**
 * A dynamic protobuf message that can represent any message without a compiled schema.
 *
 * Fields are stored as a map from field number to a list of [FieldValue] entries.
 * A list is used because repeated fields and unknown fields can have multiple values
 * for the same field number. A [ListMap] is used to preserve insertion order.
 */
class GenericMessage
        implements MessageLite {

    /**
     * The fields of this message, keyed by field number.
     *
     * Each field number maps to a list of values. For scalar (non-repeated) fields,
     * the last value in the list is the effective value (last-one-wins). For repeated
     * fields, all values are significant.
     */
    ListMap<Int, List<FieldValue>> fields = new ListMap();

    // ----- MessageLite interface -------------------------------------------------------------

    @Override
    void writeTo(CodedOutput out) {
        for ((Int fieldNumber, List<FieldValue> values) : fields) {
            for (FieldValue value : values) {
                out.writeTag(fieldNumber, value.wireType);
                value.writeTo(out);
            }
        }
    }

    @Override
    GenericMessage mergeFrom(CodedInput input) {
        while (!input.isAtEnd()) {
            Int      tag         = input.readTag();
            if (tag == 0) {
                break;
            }
            Int      fieldNumber = WireType.getFieldNumber(tag);
            WireType wireType    = WireType.getWireType(tag);

            switch (wireType) {
            case VARINT:
                addFieldValue(fieldNumber, new VarintValue(input.readVarint()));
                break;

            case I32:
                addFieldValue(fieldNumber, new Fixed32Value(input.readFixed32()));
                break;

            case I64:
                addFieldValue(fieldNumber, new Fixed64Value(input.readFixed64()));
                break;

            case LEN:
                addFieldValue(fieldNumber, new LengthValue(input.readBytes()));
                break;

            case SGROUP:
            case EGROUP:
                // Groups are deprecated; skip them
                input.skipField(wireType);
                break;
            }
        }
        return this;
    }

    @Override
    Int serializedSize() {
        Int size = 0;
        for ((Int fieldNumber, List<FieldValue> values) : fields) {
            for (FieldValue value : values) {
                size += CodedOutput.computeTagSize(fieldNumber) + value.wireSize;
            }
        }
        return size;
    }

    // ----- field access: raw FieldValue -------------------------------------------------------

    /**
     * @return True and the last FieldValue for the given field number, or False if not present
     */
    conditional FieldValue getFieldValue(Int fieldNumber) {
        if (List<FieldValue> values := fields.get(fieldNumber), !values.empty) {
            return True, values[values.size - 1];
        }
        return False;
    }

    /**
     * @return the list of all FieldValues for the given field number, or an empty list
     */
    List<FieldValue> getFieldValues(Int fieldNumber) {
        if (List<FieldValue> values := fields.get(fieldNumber)) {
            return values;
        }
        return [];
    }

    /**
     * Set a single value for a field, replacing any existing values.
     */
    void setFieldValue(Int fieldNumber, FieldValue value) {
        fields.put(fieldNumber, new Array<FieldValue>().add(value));
    }

    /**
     * Add a value to a field (appending to the list of values for repeated fields).
     */
    void addFieldValue(Int fieldNumber, FieldValue value) {
        if (List<FieldValue> values := fields.get(fieldNumber)) {
            values.add(value);
        } else {
            fields.put(fieldNumber, new Array<FieldValue>().add(value));
        }
    }

    /**
     * @return True if this message has any value for the given field number
     */
    Boolean hasField(Int fieldNumber) {
        if (List<FieldValue> values := fields.get(fieldNumber)) {
            return !values.empty;
        }
        return False;
    }

    /**
     * Remove all values for the given field number.
     */
    void clearField(Int fieldNumber) {
        fields.remove(fieldNumber);
    }

    // ----- typed convenience accessors: varint -----------------------------------------------

    /**
     * Get the varint value for the given field number (last-one-wins).
     *
     * @return the Int64 value
     *
     * @throws IllegalState if the field is not present or is not a VARINT
     */
    Int64 getVarint(Int fieldNumber) {
        assert FieldValue fv := getFieldValue(fieldNumber) as $"Field {fieldNumber} not found";
        assert fv.is(VarintValue) as $"Field {fieldNumber} is not a VARINT";
        return fv.value;
    }

    /**
     * Set a varint value for the given field number, replacing any existing value.
     */
    void setVarint(Int fieldNumber, Int64 value) {
        setFieldValue(fieldNumber, new VarintValue(value));
    }

    /**
     * Get all varint values for a repeated varint field.
     */
    List<Int64> getRepeatedVarint(Int fieldNumber) {
        List<Int64> result = new Array();
        for (FieldValue fv : getFieldValues(fieldNumber)) {
            if (fv.is(VarintValue)) {
                result.add(fv.value);
            }
        }
        return result;
    }

    /**
     * Add a varint value for a repeated field.
     */
    void addVarint(Int fieldNumber, Int64 value) {
        addFieldValue(fieldNumber, new VarintValue(value));
    }

    // ----- typed convenience accessors: string -----------------------------------------------

    /**
     * Get the string value for the given field number (last-one-wins).
     * Interprets a LEN field as a UTF-8 encoded string.
     *
     * @return the String value
     */
    String getString(Int fieldNumber) {
        immutable Byte[] data = getLengthData(fieldNumber);
        if (data.empty) {
            return "";
        }
        import ecstasy.io.ByteArrayInputStream;
        CodedInput strInput = new CodedInput(new ByteArrayInputStream(data));
        // Push a limit for the entire byte array so readString can use the length
        // Actually, we already have the raw bytes; we need to decode UTF-8 directly
        StringBuffer buf   = new StringBuffer(data.size);
        Int          index = 0;
        while (index < data.size) {
            Byte   b  = data[index++];
            UInt32 cp;
            if (b & 0x80 == 0) {
                cp = b.toUInt32();
            } else if (b & 0xE0 == 0xC0) {
                cp =  ((b & 0x1F).toUInt32() << 6)
                   | (data[index++] & 0x3F).toUInt32();
            } else if (b & 0xF0 == 0xE0) {
                cp =  ((b & 0x0F).toUInt32() << 12)
                   | ((data[index++] & 0x3F).toUInt32() << 6)
                   | (data[index++] & 0x3F).toUInt32();
            } else {
                cp =  ((b & 0x07).toUInt32() << 18)
                   | ((data[index++] & 0x3F).toUInt32() << 12)
                   | ((data[index++] & 0x3F).toUInt32() << 6)
                   | (data[index++] & 0x3F).toUInt32();
            }
            buf.add(cp.toChar());
        }
        return buf.toString();
    }

    /**
     * Set a string value for the given field number, replacing any existing value.
     */
    void setString(Int fieldNumber, String value) {
        setFieldValue(fieldNumber, new LengthValue(value.utf8()));
    }

    // ----- typed convenience accessors: bytes ------------------------------------------------

    /**
     * Get the raw bytes for the given LEN field number (last-one-wins).
     */
    immutable Byte[] getBytes(Int fieldNumber) {
        return getLengthData(fieldNumber);
    }

    /**
     * Set a bytes value for the given field number, replacing any existing value.
     */
    void setBytes(Int fieldNumber, Byte[] value) {
        setFieldValue(fieldNumber, new LengthValue(value.freeze(False)));
    }

    // ----- typed convenience accessors: fixed ------------------------------------------------

    /**
     * Get the fixed32 value for the given field number (last-one-wins).
     */
    UInt32 getFixed32(Int fieldNumber) {
        assert FieldValue fv := getFieldValue(fieldNumber) as $"Field {fieldNumber} not found";
        assert fv.is(Fixed32Value) as $"Field {fieldNumber} is not an I32";
        return fv.value;
    }

    /**
     * Set a fixed32 value for the given field number.
     */
    void setFixed32(Int fieldNumber, UInt32 value) {
        setFieldValue(fieldNumber, new Fixed32Value(value));
    }

    /**
     * Get the fixed64 value for the given field number (last-one-wins).
     */
    UInt64 getFixed64(Int fieldNumber) {
        assert FieldValue fv := getFieldValue(fieldNumber) as $"Field {fieldNumber} not found";
        assert fv.is(Fixed64Value) as $"Field {fieldNumber} is not an I64";
        return fv.value;
    }

    /**
     * Set a fixed64 value for the given field number.
     */
    void setFixed64(Int fieldNumber, UInt64 value) {
        setFieldValue(fieldNumber, new Fixed64Value(value));
    }

    // ----- typed convenience accessors: sub-message ------------------------------------------

    /**
     * Get a sub-message for the given LEN field number by parsing the raw bytes
     * as a GenericMessage.
     */
    GenericMessage getMessage(Int fieldNumber) {
        immutable Byte[] data = getLengthData(fieldNumber);
        GenericMessage msg = new GenericMessage();
        msg.mergeFromBytes(data);
        return msg;
    }

    /**
     * Set a sub-message value for the given field number.
     */
    void setMessage(Int fieldNumber, GenericMessage value) {
        setFieldValue(fieldNumber, new LengthValue(value.toByteArray()));
    }

    // ----- Freezable -------------------------------------------------------------------------

    @Override
    immutable GenericMessage freeze(Boolean inPlace = False) {
        if (this.is(immutable GenericMessage)) {
            return this;
        }

        if (inPlace) {
            fields.makeImmutable();
            return this.makeImmutable();
        }
        GenericMessage frozen = new GenericMessage();
        frozen.fields.putAll(fields);
        frozen.fields.makeImmutable();
        return frozen.makeImmutable();
    }

    // ----- internal --------------------------------------------------------------------------

    /**
     * Get the raw byte data from a LEN field value.
     */
    private immutable Byte[] getLengthData(Int fieldNumber) {
        assert FieldValue fv := getFieldValue(fieldNumber) as $"Field {fieldNumber} not found";
        assert fv.is(LengthValue) as $"Field {fieldNumber} is not a LEN";
        return fv.data;
    }
}
