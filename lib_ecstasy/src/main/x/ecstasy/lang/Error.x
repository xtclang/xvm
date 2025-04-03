/**
 * A general error representation, for logging errors (instead of throwing exceptions), such as
 * when one or more errors occur during compilation, and need to be collected for display to a
 * developer.
 *
 * An `Error` consists of a location description (the source of the error), the severity of the
 * error, an error code (the type of, or identifier for the error), and the error description, which
 * may be parameterized according to the details of the error.
 */
const Error {
    // ----- constructors --------------------------------------------------------------------------
    
    /**
     * Construct an `Error` object.
     *
     * @param severity   the severity of the error
     * @param errorCode  identity of the error message
     * @param message    the literal message or the function to use to look up the unformatted error
     *                   message using the error code string
     * @param params     the values to use to populate the parameters of the error message
     * @param location   a description of the location of the error
     */
    construct(Severity         severity,
              String|ErrorCode errorCode,
              (String|Lookup)? message  = Null,
              Object[]         params   = [],
              String?          location = Null,
             ) {
        this.severity  = severity;
        this.errorCode = errorCode;
        this.template  = message.is(String)?;
        this.lookup    = message.is(Lookup)?;
        this.params    = params;
        this.location  = location?;
    }

    // ----- types ---------------------------------------------------------------------------------

    /**
     * A function that takes an error code and returns an unformatted message.
     */
    typedef function String(String) as Lookup;

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The location information for the `Error`.
     */
    String? location;

    /**
     * The severity of the `Error`.
     */
    Severity severity;

    /**
     * Either the string identity of the error code, or an [ErrorCode] containing the identity.
     */
    String|ErrorCode errorCode;

    /**
     * The string identity of the error code corresponding to the error.
     */
    String code.get() = errorCode.is(ErrorCode)?.code : errorCode;

    /**
     * The parameters used to fill in information in the error message related to the error code.
     */
    Object[] params;

    /**
     * The function that provides an unformatted message based on an error code. This provides the
     * capability of a "localizable resource" file.
     */
    Lookup? lookup;

    /**
     * The unformatted error message for the error code. This property encapsulates the resource
     * resolution that provides an unformatted message for an error code. The order of preference
     * for obtaining the unformatted error message is (i) using the lookup function if one is
     * provided; (ii) using the message text provided explicitly to this `Error` on construction;
     * and (iii) using the default message text from the [ErrorCode], if one was provided.
     */
    String? template.get() = lookup?(code) : super() ?: errorCode.is(ErrorCode)?.message : Null;
//    String? template.get() {
//        String? s = lookup?(code) : Null;
//        if (s == Null) {
//            s = super();
//        }
//        if (s == Null) {
//            s = errorCode.message;
//        }
//        return s;
//    }

    /**
     * A formatted error message describing the error code, with the parameters included.
     */
    String? message.get() {
        String? message = template;
        if (message == Null) {
            return Null;
        }

        Int paramCount = this.params.size;
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
        String   code     = this.code;
        String?  location = this.location;
        Object[] params   = this.params;
        StringBuffer buf  = new StringBuffer();
        location?.appendTo(buf);
        buf.add(':')
           .addAll(code);
        for (Object param : params) {
            buf.add(';')
               .append(param);
        }
        return buf.toString();
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
        if (String location ?= location) {
            buf.addAll(location)
               .add(' ');
        }
        buf.addAll(code);
        if (String message ?= message) {
            buf.add(' ')
               .addAll(message);
        }
        return buf;
    }
}
