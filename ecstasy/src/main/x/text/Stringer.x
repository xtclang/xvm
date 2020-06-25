/**
 * An mixin that allows a class to optimize its rendering into a String.
 */
mixin Stringer
        into Stringable
    {
    @Override
    String toString()
        {
        StringBuffer buffer = new StringBuffer(estimateStringLength());
        appendTo(buffer);
        return buffer.toString();
        }
    }