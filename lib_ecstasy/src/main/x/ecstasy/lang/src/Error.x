import io.TextPosition;

/**
 * An Error implementation that represents its location within the text of a source code file.
 */
const Error
        extends lang.Error {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an Ecstasy source code error.
     *
     * @param severity  the severity of the error
     * @param code      the error message identity
     * @param lookup    the function to use to get the unformatted error message
     * @param params    the values to use to populate the parameters of the error message
     * @param source    the source containing the text that caused the error to be logged
     * @param before    the TextPosition of the first character (inclusive) related to the error
     * @param after     the TextPosition of the last character (exclusive) related to the error
     */
    construct(Severity severity, String code, MessageLookup lookup, Object[] params,
              Source source, TextPosition before, TextPosition after) {
        this.severity = severity;
        this.code     = code;
        this.lookup   = lookup;
        this.params   = params;
        this.source   = source;
        this.before   = before;
        this.after    = after;
    }


    // ----- types ---------------------------------------------------------------------------------

    /**
     * A function that takes an error code and returns an unformatted message.
     */
    typedef function String (String) as MessageLookup;


    // ----- properties ----------------------------------------------------------------------------

    /**
     * The source code within which the error was detected.
     */
    Source source;

    /**
     * The location within source code at which the error was detected.
     */
    TextPosition before;

    /**
     * The location within source code that represents the conclusion of the error.
     */
    TextPosition after;

    @Override
    String location.get() {
        String?      name = source.file?.name : Null;
        StringBuffer buf  = new StringBuffer(16 + (name?.size : 0));

        if (name != Null) {
            name.appendTo(buf);
            buf.add(' ');
        }

        buf.add('[');
        (before.lineNumber + 1).appendTo(buf);
        buf.add(':');
        (before.lineOffset + 1).appendTo(buf);
        if (after != before) {
            "..".appendTo(buf);
            (after.lineNumber + 1).appendTo(buf);
            buf.add(':');
            (after.lineOffset + 1).appendTo(buf);
        }
        buf.add(']');

        return buf.toString();
    }

    /**
     * The function that provides an unformatted message based on an error code. This provides the
     * capability of a "localizable resource" file.
     */
    MessageLookup lookup;

    @Override
    String unformattedMessage.get() {
        return lookup(code);
    }

    @Override
    String? context.get() {
        return before == after ? Null : source.createReader()[before..after];
    }
}
