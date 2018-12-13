class ConstHelper
    {
    /**
     * Helper function to calculate a total length of the string produced by the
     * Const.estimateStringLength() implementation including the delimiters:
     *
     * (field1=value1, field2=value2, ...)
     */
    static Int estimateStringLength(String[] names, Object[] fields)
        {
        Int c      = names.size;
        Int length = c * 3 - 1; // (=, =,) --> 2 + (fields.size - 1) * 3
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
                length += field.to<String>().size;
                }
            }
        return length;
        }

    /**
     * Helper function to append the constant fields and delimiters to the Appender.
     *
     * (field1=value1, field2=value2, ...)
     */
    static void appendTo(Appender<Char> appender, String[] names, Object[] fields)
        {
        appender.add('(');

        for (Int i = 0, Int c = names.size; i < c; i++)
            {
            if (i > 0)
                {
                appender.add(", ");
                }
            appender.add(names[i]).add('=');

            Object field = fields[i];
            if (field.is(Stringable))
                {
                field.appendTo(appender);
                }
            else
                {
                // this should never happen
                appender.add(field.to<String>());
                }
            }
        appender.add(')');
        }
    }