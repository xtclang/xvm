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
     * @param source     the source containing the text that caused the error to be logged
     * @param before     the TextPosition of the first character (inclusive) related to the error
     * @param after      the TextPosition of the last character (exclusive) related to the error
     * @param severity   the severity of the error
     * @param errorCode  identity of the error message
     * @param message    the literal message or the function to use to look up the unformatted error
     *                   message using the error code string
     * @param params     the values to use to populate the parameters of the error message
     */
    construct(Source|Reader    source,
              TextPosition     before,
              TextPosition     after,
              Severity         severity,
              String|ErrorCode errorCode,
              (String|Lookup)? message = Null,
              Object[]         params  = [],
             ) {
        construct lang.Error(severity, errorCode, message, params);
        if (source.is(Source)) {
            this.source = source;
        } else {
            this.context = after > before ? source[before..<after] : Null;
        }

        this.before = before;
        this.after  = after;
    }

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The source code, represented as a [Source] or [Reader], within which the error was detected.
     */
    Source? source;

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
        String?      name = source.is(Source)?.file?.name : Null;
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
     * The `context` is the text section that the error is related to.
     */
    String? context.get() {
        return super()?;
        if (after.offset > before.offset) {
            return source?.createReader()[before..<after];
        }
        return Null;
    }

    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        super(buf);
        if (String context ?= this.context) {
            if (context.size > 60) {
                context = context[0..57] + "...";
            }
            buf.addAll(" (\"");
            context.appendEscaped(buf);
            buf.addAll("\")");
        }
        return buf;
    }
}
