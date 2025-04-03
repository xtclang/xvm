import ecstasy.io.TextPosition;
import ecstasy.lang.src.Error;
import ecstasy.lang.ErrorList;
import ecstasy.lang.ErrorCode;
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

    /**
     * Parse an XML [Document] from the passed `String`.
     *
     * @param text
     *
     * @return `True` iff parsing succeeded
     * @return (conditional) the parsed XML [Document]
     */
    conditional Document parse(String text) {
        return parse(text.toReader());
    }

    conditional Document parse(Reader in) {
        if (!errs.empty) {
            errs = new ErrorList();
        }
        if (Document doc := parseDocument(), !errs.hasSeriousErrors) {
            return True, doc;
        }
        return False;
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
        Int     startDoc = offset;
        Parsed? firstNode = Null;
        if (match("<?xml")) {
            // TODO parse XMLDecl
        }

        while (Parsed node := parseMisc()) {
            firstNode = link(firstNode, node);
        }

        if (Parsed node := parseDocTypeDecl()) {
            firstNode = link(firstNode, node);
            while (node := parseMisc()) {
                firstNode = link(firstNode, node);
            }
        }

        if (Parsed+ElementNode node := parseElement()) { // TODO GG @Parsed ElementNode instead of Parsed+ElementNode
            firstNode = link(firstNode, node);
        } else {
            // TODO log: missing root element
            return False;
        }

        while (Parsed node := parseMisc()) {
            firstNode = link(firstNode, node);
        }

        return True, new @Parsed(startDoc, offset-startDoc) DocumentNode(firstNode);
    }

    protected Parsed link(Parsed? firstNode, Parsed trailingNode) {
        // TODO
        return firstNode ?: trailingNode;
    }

    /**
     * TODO
     *
     *    Misc	   ::=   	Comment | PI | S
     */
    conditional Parsed parseMisc() {
        return False;
    }

    /**
     * TODO
     */
    conditional Parsed parseDocTypeDecl() {
        return False;
    }

    /**
     * TODO
     */
    conditional Parsed+ElementNode parseElement() {
        return False;
    }

    // ----- lexing --------------------------------------------------------------------------------

    /**
     * Provides [Reader.eof]
     */
    protected Boolean eof.get() = reader.eof;

    /**
     * Provides gettable and settable [Reader.offset]
     */
    protected Int offset {
        @Override
        Int get() = reader.offset;

        @Override
        void set(Int newValue) {
            reader.offset = newValue;
        }
    }

    /**
     * Provides [Reader.position]
     */
    protected TextPosition position.get() = reader.position;

    /**
     * Provides [Reader.peek]
     */
    protected conditional Char peek() = reader.peek();

    /**
     * Provides [Reader.next]
     */
    protected conditional Char next() = reader.next();

    /**
     * Provides [Reader.match(Char)]
     */
    protected conditional Char match(Char ch) = reader.match(ch);

    /**
     * Provides [Reader.match(function Boolean(Char))]
     */
    protected conditional Char match(function Boolean(Char) matches) = reader.match(matches);

    /**
     * Provides [Reader] `match`-like functionality, but for an entire `String` and not just a
     * single `Char`.
     *
     * @param text  the `String` to match
     *
     * @return `True` iff the next sequence of characters in the [Reader] are identical to those in
     *         the provided `text`
     * @return (conditional) the matched String
     */
    protected conditional String match(String text) {
        Int oldOffset = offset;
        for (Char ch : text) {
            if (!match(ch)) {
                offset = oldOffset;
                return False;
            }
        }
        return True, text;
    }

    /**
     * Function: Is the character an XML space (aka `S`) character?
     *
     * From section 2.3 Common Syntactic Constructs:
     *
     *     White Space
     *     [3]   	S	   ::=   	(#x20 | #x9 | #xD | #xA)+
     *
     * @param ch  a character
     *
     * @return `True` iff the character is an XML "White Space" (aka `S`) character
     */
    protected static Boolean isSpace(Char ch) {
        return switch(ch) {
            case ' ':
            case '\r':
            case '\n':
            case '\t': True;
            default:   False;
        };
    }

    /**
     * @return `True` iff one or more XML "White Space" characters was matched
     */
    protected Boolean matchSpace() {
        if (match(isSpace)) {
            while (match(isSpace)) {}
            return True;
        }
        return False;
    }

// TODO
//    /**
//     * Match for the specified character, and log an error if it was
//     */
//    protected Boolean missing(Char ch) {
//        if (match(ch)) {
//            return False;
//        }
//
//        err("Unexpected character: {peek().quoted()}");
//        return True;
//    }

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
    protected Boolean log(Severity severity, ErrorMsg errmsg, Object[] params, TextPosition before, TextPosition after) {
        return errs.log(new Error(reader, before, after, severity, errmsg, Null, params));
    }

    /**
     * Error codes.
     *
     * While it may appear that the error messages are hard-coded, the text found here is simply
     * the default error text; it can eventually be localized as necessary.
     */
    enum ErrorMsg(String code, String message)
            implements ErrorCode {
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
