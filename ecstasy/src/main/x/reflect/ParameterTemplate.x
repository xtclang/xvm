/**
 * Represents the compiled information for a method parameter or return value.
 */
interface ParameterTemplate
        extends Stringable
    {
    /**
     * The name of the parameter or return value, or null if there is none.
     */
    String? name;

    /**
     * The index of the parameter or return value.
     */
    Int index;

    /**
     * The type of the parameter or return value. Always `Boolean` for ConditionalReturn category,
     * or the type of constraint's type for TypeParameter.
     */
    TypeTemplate type;

    /**
     * The default value, which may be Null, or Null if there is no default.
     */
    Const? defaultValue;

    /**
     * The Category enum.
     */
    enum Category(Boolean isParameter, Boolean isReturn, Boolean hasDefault)
        {
        RegularParameter (True,  False, False),
        DefaultParameter (True,  False, True ),
        TypeParameter    (True,  False, False),
        RegularReturn    (False, True,  False),
        ConditionalReturn(False, True,  False),
        }

    /**
     * The category of the parameter or return value.
     */
    Category category;


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength()
        {
        Int     length = type.estimateStringLength();
        String? name   = this.name;
        if (name != Null)
            {
            length += name.size;
            if (category == DefaultParameter)
                {
                length += 3 + defaultValue.estimateStringLength();
                }
            }
        return length;
        }

    @Override
    Appender<Char> appendTo(Appender<Char> buf)
        {
        type.appendTo(buf);

        String? name = this.name;
        if (name != Null)
            {
            buf.add(' ');
            name.appendTo(buf);
            if (category == DefaultParameter)
                {
                " = "       .appendTo(buf);
                defaultValue.appendTo(buf);
                }
            }
        return buf;
        }
    }
