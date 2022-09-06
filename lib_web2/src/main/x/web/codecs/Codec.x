import ecstasy.io.ByteArrayInputStream;
import ecstasy.io.ByteArrayOutputStream;

/**
 * Represents a codec for a particular media type. For example application/json.
 */
interface Codec
        extends Const
    {
    /**
     * Return the MediaTypes supported by this codec.
     *
     * @return The media types supported by this codec
     */
    @RO MediaType[] mediaTypes;

    /**
     * Decode the given type from the given InputStream.
     *
     * @param type  the Type of the value to decode
     * @param in    the input stream containing the data to decode
     *
     * @return The decoded result
     */
    <ObjectType> ObjectType decode<ObjectType>(Type type, InputStream in);

    /**
     * Decode the given type from the given InputStream.
     *
     * @param type   the Type of the value to decode
     * @param bytes  the byte array containing the data to decode
     *
     * @return The decoded result
     */
    <ObjectType> ObjectType decode<ObjectType>(Type type, Byte[] bytes)
        {
        return decode<ObjectType>(type, new ByteArrayInputStream(bytes));
        }

    /**
     * Encode the given value to the given {@link OutputStream}.
     *
     * @param value  the value to encode
     * @param out    the output stream to encode the value to
     */
    <ObjectType> void encode<ObjectType>(ObjectType value, OutputStream out);

    /**
     * Encode the given value to a byte array.
     *
     * @param value  the value to encode
     *
     * @return  the encoded value
     */
    <ObjectType> Byte[] encode<ObjectType>(ObjectType value)
        {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        encode(value, out);
        return out.bytes;
        }

    /**
     * Return whether this codec can decode the given type.
     *
     * @param type the type to decode or encode
     *
     * @return True if this codec supports the specified Type
     */
    Boolean supports(Type type)
        {
        return True;
        }
    }
