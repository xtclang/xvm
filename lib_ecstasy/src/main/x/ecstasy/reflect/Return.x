/**
 * Represents a function return value.
 */
interface Return<ReturnType>
        extends immutable Const
    {
    /**
     * The ordinal index of the return value.
     */
    @RO Int ordinal;

    /**
     * Determine the return value name.
     *
     * @return True iff the return value has a name
     * @return (conditional) the return value name
     */
    conditional String hasName();


    // ----- Stringable methods ----------------------------------------------------------------

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
