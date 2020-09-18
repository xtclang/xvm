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
     * Helper function to "freeze" all the fields for a Const class.
     *
     * Note: we don't want to throw a natural exception from here as it will show the native
     *       class in the stack trace
     *
     * @return -1 if all objects were immutable; -2 if a copy needs to be made and a positive;
     *         a positive number for a field index that is not freezable
     */
    static Int freeze(Object[] fields)
        {
        import ecstasy.collections.Freezable;

        Int result = -1;
        for (Int i = 0, Int c = fields.size; i < c; i++)
            {
            Object field = fields[i];
            if (!field.is(immutable Object) && !field.is(Service))
                {
                if (field.is(Freezable))
                    {
                    fields[i] = field.freeze();
                    result = -2;
                    }
                else
                    {
                    return i;
                    }
                }
            }
        return result;
        }

    /**
     * Helper function to create an immutable ListSet out of an array.
     */
    static <Element> immutable ListSet<Element> createListSet(immutable Element[] values)
        {
        return new ListSet<Element>(values).makeImmutable();
        }
    }