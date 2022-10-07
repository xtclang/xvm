/**
 * A Format that allows an existing Format to be explicitly type-narrowed.
 */
const NarrowingFormat<Wide, Value extends Wide>(Format<Wide> format)
        implements Format<Value>
    {
    construct(Format<Wide> format)
        {
        this.format = format;
        this.name   = $"{format.name}.as({Value})";
        }

    @Override
    <OtherValue> conditional Format<OtherValue> forType(Type<OtherValue> type, Registry registry)
        {
        if (type == format.Value)
            {
            return True, format.as(Format<OtherValue>);
            }

        if (Format<OtherValue> newFormat := format.forType(type.DataType, registry))
            {
            return True, newFormat;
            }

        return False;
        }

    @Override
    Value read(Iterator<Char> stream)
        {
        return format.read(stream).as(Value);
        }

    @Override
    Value decode(String text)
        {
        return format.decode(text).as(Value);
        }

    @Override
    void write(Value value, Appender<Char> stream)
        {
        format.write(value, stream);
        }

    @Override
    String encode(Value value)
        {
        return format.encode(value);
        }
    }