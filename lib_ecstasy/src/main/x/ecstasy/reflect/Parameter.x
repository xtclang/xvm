/**
 * Represents a function parameter, including type parameters.
 */
interface Parameter<ParamType>
        extends immutable Const
    {
    /**
     * The ordinal index of the parameter.
     */
    @RO Int ordinal;

    /**
     * Determine the parameter name.
     *
     * @return True iff the parameter has a name
     * @return (conditional) the parameter name
     */
    conditional String hasName();

    /**
     * Indicates whether the parameter is a formal type parameter.
     */
    @RO Boolean formal;

    /**
     * Determine the default argument value for the parameter, if any.
     *
     * @return True iff the parameter has a default argument value
     * @return (conditional) the default argument value
     */
    conditional ParamType defaultValue();


    // ----- Stringable methods ----------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        Int len = ParamType.estimateStringLength();
        if (String name := hasName())
            {
            len += 1 + name.size;
            }
        return len;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        ParamType.appendTo(buf);
        if (String name := hasName())
            {
            buf.add(' ');
            name.appendTo(buf);
            }
        return buf;
        }
    }
