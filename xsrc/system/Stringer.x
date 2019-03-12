/**
 * An mixin that allows a class to optimize its rendering into a String.
 */
mixin Stringer
        into Stringable
    {
    @Override
    String to<String>()
        {
        StringBuffer buffer = new StringBuffer(estimateStringLength());
        appendTo(buffer);
        return buffer.to<String>();
        }
    }