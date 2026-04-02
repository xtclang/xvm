/**
 * A base class for [MessageLite] implementations.
 *
 * By default, all fields are treated as unknown and stored in the [unknownFields] set. Subclasses
 * override [parseField], [writeKnownFields], and [knownFieldsSize] to handle specific fields, while
 * unrecognized fields automatically pass through the unknown field storage for faithful
 * round-tripping.
 */
class AbstractMessage
        implements MessageLite, Duplicable {

    /**
     * Construct a new message.
     */
    construct() {
        unknownFields = new UnknownFieldSet();
    }

    /**
     * Construct a new message.
     */
    construct(UnknownFieldSet? fields) {
        unknownFields = fields ?: new UnknownFieldSet();
    }

    /**
     * Construct a new message by copying the fields from the given message.
     */
    @Override
    construct(AbstractMessage other) {
        if (other.is(immutable AbstractMessage)) {
            unknownFields = other.unknownFields;
        } else {
            unknownFields = other.unknownFields.duplicate();
        }
    }

    /**
     * The set of unknown fields encountered during deserialization.
     */
    UnknownFieldSet unknownFields;

    // ----- MessageLite interface -----------------------------------------------------------------

    @Override
    void writeTo(CodedOutput out) {
        writeKnownFields(out);
        unknownFields.writeTo(out);
    }

    @Override
    AbstractMessage mergeFrom(CodedInput input) {
        while (!input.isAtEnd()) {
            Int tag = input.readTag();
            if (tag == 0) {
                break;
            }
            if (!parseField(input, tag)) {
                unknownFields.mergeFieldFrom(input, tag);
            }
        }
        return this;
    }

    @Override
    Int serializedSize() =
        knownFieldsSize() + unknownFields.serializedSize();

    // ----- extension points for subclasses -------------------------------------------------------

    /**
     * Attempt to parse a field from the input as a known field.
     *
     * Subclasses override this method to handle fields they recognize. If the field is recognized
     * and consumed, return `True`. If the field is not recognized, return `False` and the field
     * will be stored as an unknown field.
     *
     * The tag has already been read from the input. The field number and wire type can be extracted
     * using [WireType.getFieldNumber] and [WireType.getWireType].
     *
     * @param input  the coded input to read the field value from
     * @param tag    the tag that was already read
     *
     * @return True if the field was handled, False to store as unknown
     */
    Boolean parseField(CodedInput input, Int tag) = False;

    /**
     * Write all known fields to the given output.
     *
     * Subclasses override this method to serialize their typed fields.
     *
     * @param out  the coded output to write to
     */
    void writeKnownFields(CodedOutput out) {
    }

    /**
     * Compute the serialized size of all known fields.
     *
     * Subclasses override this method to report the size of their typed fields.
     *
     * @return the number of bytes required for known fields
     */
    Int knownFieldsSize() = 0;

    @Override
    immutable AbstractMessage freeze(Boolean inPlace = False) {
        if (this.is(immutable AbstractMessage)) {
            return this;
        }

        unknownFields.freeze(inPlace);
        return this.makeImmutable();
    }
}
