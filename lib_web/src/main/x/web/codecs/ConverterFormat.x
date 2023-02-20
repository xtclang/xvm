/**
 * A Format that converts to and from another underlying Format.
 */
const ConverterFormat<Raw, Value>(Format<Raw>            format,
                                  Converter<Raw, Value>? up,
                                  Converter<Value, Raw>? down)
        implements Format<Value>
    {
    assert()
        {
        name = $"{format.name}->{Value}";

        assert up != Null || down != Null
                as $"At least one converter between {Raw} and {Value} is required";
        }

    @Override
    <OtherValue> conditional Format<OtherValue> forType(Type<OtherValue> type, Registry registry)
        {
        if (type == format.Value)
            {
            return True, format.as(Format<OtherValue>);
            }

        if (Format<OtherValue> newFormat := format.forType(type, registry))
            {
            return True, newFormat;
            }

        return False;
        }

    @Override
    Value read(Iterator<Char> stream)
        {
        assert Converter<Raw, Value> up ?= this.up as $"Format {name.quoted()} is write-only";
        return up.convert(format.read(stream));
        }

    @Override
    Value decode(String text)
        {
        assert Converter<Raw, Value> up ?= this.up as $"Format {name.quoted()} is write-only";
        return up.convert(format.decode(text));
        }

    @Override
    void write(Value value, Appender<Char> stream)
        {
        assert Converter<Value, Raw> down ?= this.down as $"Format {name.quoted()} is read-only";
        format.write(down.convert(value), stream);
        }

    @Override
    String encode(Value value)
        {
        assert Converter<Value, Raw> down ?= this.down as $"Format {name.quoted()} is read-only";
        return format.encode(down.convert(value));
        }
    }