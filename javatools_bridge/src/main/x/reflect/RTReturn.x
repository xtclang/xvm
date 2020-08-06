import ecstasy.reflect.Return;

/**
 * Return value implementation.
 */
const RTReturn<ReturnType>(Int ordinal, String? name)
        implements Return<ReturnType>
    {
    @Override
    conditional String hasName()
        {
        return name == Null
                ? False
                : (True, name.as(String));
        }

    // Stringable implementation is a copy from Return interface
    @Override
    Int estimateStringLength()
        {
        Int len = ReturnType.estimateStringLength();
        if (String name := hasName())
            {
            len += 1 + name.size;
            }
        return len;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        ReturnType.appendTo(buf);
        if (String name := hasName())
            {
            buf.add(' ');
            name.appendTo(buf);
            }
        return buf;
        }
    }