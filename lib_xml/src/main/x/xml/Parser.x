import ecstasy.io.TextPosition;
import ecstasy.lang.src.Error;
import ecstasy.lang.ErrorList;
import ecstasy.lang.Severity;

import impl.*;

/**
 * An XML `Parser`.
 */
class Parser(Boolean ignoreProlog       = False,
             Boolean ignoreComments     = False,
             Boolean ignoreInstructions = False,
            ) {

    // ----- properties ----------------------------------------------------------------------------

    /**
     * The [Reader] available while parsing.
     */
    protected Reader reader.get() = reader_ ?: assert;

    /**
     * The storage that holds the [Reader] while parsing.
     */
    private Reader? reader_;

    /**
     * The ErrorList.
     */
    ErrorList errs = new ErrorList();

    // ----- API -----------------------------------------------------------------------------------

    conditional Document parse(String text) {
        return parse(text.toReader());
    }

    conditional Document parse(Reader in) {
        errs.reset();
        TODO
    }

    // ----- parsing -------------------------------------------------------------------------------

    /**
     * TODO
     *
     * optional: XMLDecl "<?xml"
     * optional: Misc*
     * optional: doctypedecl "<!DOCTYPE"
     * optional: Misc*
     * required: Element
     * optional: Misc*
     *
     * Misc: Comment, PI, S
     * Comment: "<!--"
     * PI: "<?" PITarget
     */
    protected conditional Document parseDocument() {
        Int startDoc = pos;
        if (missing('<')) {
            return False;
        }

        if (match('?')) {
            // TODO
        } else if (match('!')) {
            // TODO
        } else {
            err("Unexpected character: {peek().quoted()}");
            return False;
        }

        ElementNode elem;
        TODO
    }

    // ----- lexing --------------------------------------------------------------------------------

    /**
     * TODO
     */
    protected Boolean eof;

    /**
     * The next Char
     */
    protected Char ch;

    protected conditional Char peek() = reader.peek();

    protected conditional Char next() = reader.next();

    /**
     * TODO
     */
    protected Int pos {
        @Override
        Int get() = reader.offset;

        @Override
        void set(Int n) {
            reader.offset = n;
        }
    }

    /**
     * TODO
     */
    protected Boolean missing(Char ch) {
        if (match(ch)) {
            return False;
        }

        err("Unexpected character: {peek().quoted()}");
        return True;
    }

    /**
     * TODO
     */
    protected conditional Char match(Char ch) = reader.match(ch);

    /**
     * Log an error.
     *
     * @return `False`
     */
    protected Boolean err(String text) {
        // TODO: errors.add($"[{lineNumber}:{lineOffset}] ({offset}) {text}");
        return False;
    }

    // ----- error handling ------------------------------------------------------------------------

    /**
     * Log an error.
     *
     * @param severity  the severity of the error
     * @param msg       the error message identity
     * @param params    the values to use to populate the parameters of the error message
     * @param start     the TextPosition of the first character (inclusive) related to the error
     * @param end       the TextPosition of the last character (exclusive) related to the error
     *
     * @return True indicates that the process that reported the error should attempt to abort at
     *         this point if it is able to
     */
    protected Boolean log(Severity severity, ErrorMsg msg, Object[] params, TextPosition start, TextPosition end) {
        return errs.log(new Error(severity, msg.code, ErrorMsg.lookup, params, reader, start, end));
    }

    /**
     * Error codes.
     *
     * While it may appear that the error messages are hard-coded, the text found here is simply
     * the default error text; it will eventually be localized as necessary.
     */
    enum ErrorMsg(String code, String message) {
        Example         ("XML-01", "Unexpected End-Of-File (SUB character)."),
        Example2        ("XML-02", "Bad version value."),
        ;

        /**
         * Message  token ids, but not including context-sensitive keywords.
         */
        static Map<String, ErrorMsg> byCode = {
            HashMap<String, ErrorMsg> map = new HashMap();
            for (ErrorMsg errmsg : ErrorMsg.values) {
                map[errmsg.code] = errmsg;
            }
            return map.makeImmutable();
        };

        /**
         * Lookup unformatted error message by error code.
         */
        static String lookup(String code) {
            assert ErrorMsg err := byCode.get(code);
            return err.message;
        }
    }
}
