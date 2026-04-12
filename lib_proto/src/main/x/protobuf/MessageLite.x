import ecstasy.io.ByteArrayInputStream;

/**
 * The base interface for Protocol Buffers lite messages.
 *
 * A MessageLite represents a protobuf message that supports serialization and deserialization using
 * the binary wire format. This is the lightweight variant; a full `Message` type with descriptor
 * and reflection support will be added later.
 */
interface MessageLite
    extends Freezable {

    /**
     * Serialize this message to the given [CodedOutput].
     *
     * @param out  the coded output to write to
     */
    void writeTo(CodedOutput out);

    /**
     * Serialize this message to the given [OutputStream].
     *
     * @param out  the coded output to write to
     */
    void writeTo(OutputStream out) {
        writeTo(new CodedOutput(out));
    }

    /**
     * Deserialize fields from the given [CodedInput] and merge them into this message.
     *
     * For scalar fields, the last value seen wins. For embedded messages, the values are merged.
     * For repeated fields, the values are appended.
     *
     * Implementations that are mutable may update in place and return `this`. Implementations that
     * are `const` should return a new instance with the merged fields.
     *
     * @param input  the coded input to read from
     *
     * @return the message with the merged fields (may be `this` for mutable implementations)
     */
    MessageLite! mergeFrom(CodedInput input);

    /**
     * Deserialize fields from the given [InputStream] and merge them into this message.
     *
     * For scalar fields, the last value seen wins. For embedded messages, the values are merged.
     * For repeated fields, the values are appended.
     *
     * Implementations that are mutable may update in place and return `this`. Implementations that
     * are `const` should return a new instance with the merged fields.
     *
     * @param input  the coded input to read from
     *
     * @return the message with the merged fields (may be `this` for mutable implementations)
     */
    MessageLite! mergeFrom(InputStream input) {
        return mergeFrom(new CodedInput(input));
    }

    /**
     * Merge a message into this one.
     *
     * @param other  the message to merge from
     *
     *  @return if this message is immutable a new message with the merged fields, otherwise this
     *          message
     */
    MessageLite! mergeFrom(MessageLite other);

    /**
     * @return the number of bytes required to serialize this message (excluding any tag or length
     *             prefix that would be written by an enclosing message)
     */
    Int serializedSize();

    /**
     * Serialize this message to a new byte array.
     *
     * @return the serialized bytes
     */
    immutable Byte[] toByteArray() {
        import ecstasy.io.ByteArrayOutputStream;

        ByteArrayOutputStream buf = new ByteArrayOutputStream(serializedSize());
        CodedOutput out = new CodedOutput(buf);
        writeTo(out);
        return buf.bytes.freeze(inPlace=True);
    }

    /**
     * Deserialize a message from a byte array by merging into this message.
     *
     * @param bytes  the serialized protobuf bytes
     */
    MessageLite mergeFromBytes(Byte[] bytes) {
        CodedInput input = new CodedInput(new ByteArrayInputStream(bytes));
        return mergeFrom(input);
    }
}
