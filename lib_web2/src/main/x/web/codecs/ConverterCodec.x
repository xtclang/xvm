/**
 * A Codec that converts to and from another underlying Codec.
 */
const ConverterCodec<Raw, Value>(Codec<Raw>             codec,
                                 Converter<Raw, Value>? up,
                                 Converter<Value, Raw>? down)
        implements Codec<Value>
    {
    assert()
        {
        name = $"{codec.name}->{Value}";

        assert up != Null || down != Null
                as "At least one converter between {Raw} and {Value} is required";
        }

    @Override
    <OtherValue> conditional Codec<OtherValue> forType(Type<OtherValue> type, Registry registry)
        {
        if (Codec<OtherValue> newCodec := codec.forType(type.DataType, registry))
            {
            return True, newCodec;
            }

        return False;
        }

    @Override
    Value read(InputStream stream)
        {
        assert Converter<Raw, Value> up ?= this.up as $"Codec {name.quoted()} is write-only";
        return up.convert(codec.read(stream));
        }

    @Override
    Value decode(Byte[] bytes)
        {
        assert Converter<Raw, Value> up ?= this.up as $"Codec {name.quoted()} is write-only";
        return up.convert(codec.decode(bytes));
        }

    @Override
    void write(Value value, OutputStream stream)
        {
        assert Converter<Value, Raw> down ?= this.down as $"Codec {name.quoted()} is read-only";
        codec.write(down.convert(value), stream);
        }

    @Override
    Byte[] encode(Value value)
        {
        assert Converter<Value, Raw> down ?= this.down as $"Codec {name.quoted()} is read-only";
        return codec.encode(down.convert(value));
        }
    }
