import ecstasy.io.ByteArrayInputStream;
import ecstasy.io.ByteArrayOutputStream;


/**
 * Represents the ability to encode a `Value` as a `Byte[]` of a specific binary format, and to
 * decode a `Byte[]` of that same format into a `Value`. The binary format is referred to as a
 * "codec".
 *
 * Implementations of this interface should be `const`, immutable, [Freezable], or a service.
 */
interface Codec<Value>
    {
    /**
     * The `Codec` name.
     *
     * Using this information, a `Codec` can be specified by its name. For a `Codec` that is
     * registered, this name is used by the [Registry] to look up the `Codec` by its name, such as
     * "UTF8-String" or "UTF8-Stream".
     *
     * The name is also obviously useful for debugging and log output.
     */
    @RO String name;

    /**
     * Obtain a derivative `Codec` of this `Codec` for the specified type, if such a derivative
     * `Codec` is possible.
     *
     * @param type  a `Type` for which this `Codec` may be able to supply a derivative `Codec` for
     */
    <OtherValue> conditional Codec!<OtherValue> forType(Type<OtherValue> type, Registry registry)
        {
        // if this codec is capable of translating to and from another more specific type, then
        // this method should return a Codec instance that can translate to and from the specified
        // type
        return False;
        }

    /**
     * Convert from a byte stream to a value.
     *
     * @param stream  the stream of bytes to convert to a value
     *
     * @return the resulting value
     */
    Value read(InputStream stream)
        {
        // default implementation is to suck the contents stream into a Byte[] and just delegate
        // to the decode() method; this will cause a stack overflow if at least one of these two
        // methods is not overridden
        return decode(stream.readBytes(stream.remaining));
        }

    /**
     * Convert from a `Byte[]` to a value.
     *
     * @param bytes  the `Byte[]` to convert to a value
     *
     * @return the resulting value
     */
    Value decode(Byte[] bytes)
        {
        // default implementation is to turn the string into a stream and just delegate to the
        // fromStream() method; this will cause a stack overflow if at least one of these two
        // methods is not overridden
        return read(new ByteArrayInputStream(bytes));
        }

    /**
     * Render a value into the provided stream.
     *
     * @param value   the value to convert to bytes
     * @param stream  the stream to write the bytes into
     */
    void write(Value value, OutputStream stream)
        {
        // default implementation is to just delegate to the encode() method; this will cause a
        // stack overflow if neither of these two methods is overridden
        stream.writeBytes(encode(value));
        }

    /**
     * Render a value as a `Byte[]`.
     *
     * @param value  the value to convert to a `Byte[]`
     *
     * @return the resulting `Byte[]`
     */
    Byte[] encode(Value value)
        {
        // default implementation is to just delegate to the write() method; this will cause a
        // stack overflow if neither of these two methods is overridden
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        write(value, stream);
        return stream.bytes;
        }
    }