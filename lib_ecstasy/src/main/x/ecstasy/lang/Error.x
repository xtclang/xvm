/**
 * An abstract error representation, for logging errors (instead of throwing exceptions), such as
 * when one or more errors occur during compilation, and need to be collected for display to a
 * developer.
 */
@Abstract const Error
        implements Stringable {
    // ----- properties ----------------------------------------------------------------------------

    /**
     * The location information for the error.
     */
    @RO String location;

    /**
     * The severity of the error information.
     */
    Severity severity;

    /**
     * The error code corresponding to the error.
     */
    String code;

    /**
     * The parameters used to fill in information in the error message related to the error code.
     */
    Object[] params;

    /**
     * The unformatted error message for the error code. (This property encapsulates the resource
     * resolution that provides an unformatted message for an error code.)
     */
    @RO String unformattedMessage;

    /**
     * A formatted error message describing the error code, with the parameters included.
     */
    String message.get() {
        String message    = unformattedMessage;
        Int    paramCount = this.params.size;
        if (paramCount > 0) {
            enum State {Normal, Escaped, Param, Number}

            StringBuffer buf   = new StringBuffer(message.size + paramCount * 10);
            State        state = Normal;
            Int          num   = 0;

            for (Int offset = 0, Int length = message.size; offset < length; ++offset) {
                Char ch = message[offset];
                switch (state, ch) {
                case (Escaped, 'n'):
                    buf.add('\n');
                    state = Normal;
                    break;
                case (Escaped, 'r'):
                    buf.add('\r');
                    state = Normal;
                    break;
                case (Escaped, 't'):
                    buf.add('\t');
                    state = Normal;
                    break;
                case (Escaped, '{'):
                    buf.add('{');
                    state = Normal;
                    break;
                case (Escaped, '\''):
                    buf.add('\'');
                    state = Normal;
                    break;
                case (Escaped, '\"'):
                    buf.add('\"');
                    state = Normal;
                    break;
                case (Escaped, '\\'):
                    buf.add('\\');
                    state = Normal;
                    break;
                case (Escaped, _):
                    // bad escape; print it as-is
                    buf.add('\\')
                       .add(ch);
                    state = Normal;
                    break;

                case (Param, '0'..'9'):
                    assert num := ch.asciiDigit();
                    state = Number;
                    break;
                case (Param, _):
                    // bad param; print it as-is
                    buf.add('{')
                       .add(ch);
                    state = Normal;
                    break;

                case (Number, '0'..'9'):
                    assert Int digit := ch.asciiDigit();
                    num = num * 10 + digit;
                    break;
                case (Number, '}'):
                    // if the parameter index is out of range, then assume it should be blank
                    if (num < paramCount) {
                        buf.append(params[num]);
                    }
                    state = Normal;
                    break;
                case (Number, _):
                    // bad param; print it as-is
                    buf.add('{');
                    num.appendTo(buf);
                    buf.add(ch);
                    state = Normal;
                    break;

                case (Normal, '\\'):
                    state = Escaped;
                    break;
                case (Normal, '{'):
                    state = Param;
                    break;
                default:
                    buf.add(ch);
                    break;
                }
            }

            // spit out any accrued left-over from a poorly formatted message
            switch (state) {
            case Escaped:
                buf.add('\\');
                break;
            case Param:
                buf.add('{');
                break;
            case Number:
                buf.add('{');
                num.appendTo(buf);
                break;
            }

            message = buf.toString();
        }

        return message;
    }

    /**
     * A string value that can be used to quickly detect likely-duplicate errors.
     */
    String uniqueId.get() {
        String       location = this.location;
        String       code     = this.code;
        StringBuffer buf      = new StringBuffer(location.size + 1 + code.size);
        buf.addAll(location)
           .add(':')
           .addAll(code);
        return buf.toString();
    }

    /**
     * An optional String that provides some context for the error, such as a snippet of source
     * code.
     */
    @RO String? context.get() {
        return Null;
    }


    // ----- Stringable methods --------------------------------------------------------------------

    @Override
    Int estimateStringLength() {
        // unfortunately, there are too many allocations required to build a suitable estimate of
        // the buffer size, which would cost far more than simply resizing the buffer as needed
        return 120;
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf) {
        buf.addAll(location)
           .add(' ')
           .addAll(message);

        if (String context ?= this.context) {
            if (context.size > 60) {
                context = context[0..57] + "...";
            }

            buf.addAll(" (");
            context.appendEscaped(buf);
            buf.add(')');
        }

        return buf;
    }
}
