/**
 * A Format that uses an underlying `Format<Byte[]>` to transform text to binary, and then an
 * underlying `Codec` to translate to a target `Value` type.
 *
 * One common example is combining a `Base64Format` to a `Utf8Codec`, allowing Unicode data to be
 * translated into the set of Base64 characters that are legal in various parts of the URL, HTTP
 * headers, etc.
 */
const CodecFormat<Value>(Format<Byte[]> format, Codec<Value> codec)
        implements Format<Value>
    {
    assert()
        {
        name = $"{format.name}->{codec.name}";
        }

    @Override
    <OtherValue> conditional Format<OtherValue> forType(Type<OtherValue> type, Registry registry)
        {
        if (type == Byte[])
            {
            return True, format.as(Format<OtherValue>);
            }

        if (Format<OtherValue> newFormat := format.forType(type.DataType, registry))
            {
            return True, newFormat;
            }

        if (Codec<OtherValue> newCodec := codec.forType(type.DataType, registry))
            {
            return True, new CodecFormat<OtherValue>(format, newCodec);
            }

        return False;
        }

    @Override
    Value read(Iterator<Char> stream)
        {
        Byte[] bytes = format.read(stream);
        return codec.decode(bytes);
        }

    @Override
    Value decode(String text)
        {
        Byte[] bytes = format.decode(text);
        return codec.decode(bytes);
        }

    @Override
    void write(Value value, Appender<Char> stream)
        {
        Byte[] bytes = codec.encode(value);
        format.write(bytes, stream);
        }

    @Override
    String encode(Value value)
        {
        Byte[] bytes = codec.encode(value);
        return format.encode(bytes);
        }
    }
