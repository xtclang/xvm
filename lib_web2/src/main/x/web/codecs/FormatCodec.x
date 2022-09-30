/**
 * A Codec that uses an underlying `Codec<String>` to translate from binary to text, and then an
 * underlying `Format` to translate to a target `Value` type.
 *
 * One common example is combining a `UTF8Codec` with a `JsonFormat`, allowing a message body to be
 * converted to or from a JSON document.
 */
const FormatCodec<Value>(Codec<String> codec, Format<Value> format)
        implements Codec<Value>
    {
    assert()
        {
        name = $"{codec.name}->{format.name}";
        }

    @Override
    <OtherValue> conditional Codec<OtherValue> forType(Type<OtherValue> type, Registry registry)
        {
        if (type == String)
            {
            return True, codec.as(Codec<OtherValue>);
            }

        if (Codec<OtherValue> newCodec := codec.forType(type.DataType, registry))
            {
            return True, newCodec;
            }

        if (Format<OtherValue> newFormat := format.forType(type.DataType, registry))
            {
            return True, new FormatCodec<OtherValue>(codec, newFormat);
            }

        return False;
        }

    @Override
    Value read(InputStream stream)
        {
        String text = codec.read(stream);
        return format.decode(text);
        }

    @Override
    Value decode(Byte[] bytes)
        {
        String text = codec.decode(bytes);
        return format.decode(text);
        }

    @Override
    void write(Value value, OutputStream stream)
        {
        String text = format.encode(value);
        codec.write(text, stream);
        }

    @Override
    Byte[] encode(Value value)
        {
        String text = format.encode(value);
        return codec.encode(text);
        }
    }
