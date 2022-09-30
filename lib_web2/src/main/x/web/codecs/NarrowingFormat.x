/**
 * A Format that allows an existing Format to be explicitly type-narrowed.
 */
const NarrowingFormat<Value, Wide extends Value>(Format<Wide> format)
        implements Format<Value>
    {
    assert()
        {
        name = $"{format.name}.as({format.Value})";
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
        format.write(value.as(Wide), stream);               // TODO GG get rid of .as()
        }

    @Override
    String encode(Value value)
        {
        return format.encode(value.as(Wide));               // TODO GG get rid of .as()
        }
    }
