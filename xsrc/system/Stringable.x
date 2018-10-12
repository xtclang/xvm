/**
 * An interface that allows a class to optimize its rendering into a String.
 */
interface Stringable
    {
    /**
     * Estimate the number of characters that this Stringable object will use in its String form.
     *
     * @return the number of characters that this object estimates it will need for its String form
     */
    Int estimateStringLength();

    /**
     * Append the String form of this Stringable object to the provided character Appender.
     *
     * @param appender  the Appender to append the String form of this object to
     * @param format    for types that have the ability to format themselves, such as a date or a
     *                  decimal value, this parameter may optionally specify a format string;
     *                  otherwise this value is ignored
     */
    void appendTo(Appender<Char> appender, String? format = null);
    }
