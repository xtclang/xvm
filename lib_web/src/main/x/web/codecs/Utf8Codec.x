/**
 * Represents the ability to convert a String to and from UTF-8 binary data.
 */
static const Utf8Codec
        implements Codec<String>
    {
    @Override
    String name = "UTF8-String";

    @Override
    Value decode(Byte[] bytes)
        {
        return bytes.unpackUtf8();
        }

    @Override
    void write(Value value, OutputStream stream)
        {
        for (Char ch : value)
            {
            DataOutput.writeUTF8Char(stream, ch);
            }
        }

    @Override
    Byte[] encode(Value value)
        {
        return value.utf8();
        }
    }
