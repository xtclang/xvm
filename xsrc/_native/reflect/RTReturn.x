import Ecstasy.reflect.Return;

/**
 * Return value implementation.
 */
const RTReturn<ReturnType>(Int ordinal, String? name)
        implements Return<ReturnType>
    {
    @Override
    conditional String hasName()
        {
        return name == null
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
    void appendTo(Appender<Char> appender)
        {
        ReturnType.appendTo(appender);
        if (String name := hasName())
            {
            appender.add(' ')
                    .add(name);
            }
        }
    }