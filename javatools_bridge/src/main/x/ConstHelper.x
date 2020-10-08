class ConstHelper
    {
    /**
     * Helper function to calculate a total length of the string produced by the
     * Const.estimateStringLength() implementation including the delimiters:
     *
     * (field1=value1, field2=value2, ...)
     */
    static Int estimateStringLength(immutable String[] names, immutable Object[] fields)
        {
        Int c      = names.size;
        Int length = c == 0 ? 0 : c * 3 - 1; // (=, =,) --> 2 + (fields.size - 1) * 3
        for (Int i = 0; i < c; i++)
            {
            length += names[i].size;

            Object field = fields[i];
            if (field.is(Stringable))
                {
                length += field.estimateStringLength();
                }
            else
                {
                // this should never happen
                length += field.toString().size;
                }
            }
        return length;
        }

    /**
     * Helper function to append the Const fields and delimiters to the Appender.
     *
     * (field1=value1, field2=value2, ...)
     */
    static Appender<Char> appendTo(Appender<Char> buf, immutable String[] names, immutable Object[] fields)
        {
        buf.add('(');

        for (Int i = 0, Int c = names.size; i < c; i++)
            {
            if (i > 0)
                {
                ", ".appendTo(buf);
                }
            names[i].appendTo(buf);
            buf.add('=');

            Object field = fields[i];
            if (field.is(Stringable))
                {
                field.appendTo(buf);
                }
            else
                {
                // this should never happen
                field.toString().appendTo(buf);
                }
            }
        return buf.add(')');
        }

    /**
     * Helper function to "freeze" all the Freezable fields for a Const class.
     */
    static void freeze(Object[] fields)
        {
        import ecstasy.collections.Freezable;

        Freeze:
        for (Object field : fields)
            {
            assert field.is(Freezable);

            fields[Freeze.count] = field.freeze();
            }
        }

    /**
     * Helper function to create an immutable ListSet out of an array.
     */
    static <Element> immutable ListSet<Element> createListSet(immutable Element[] values)
        {
        return new ListSet<Element>(values).makeImmutable();
        }
    }