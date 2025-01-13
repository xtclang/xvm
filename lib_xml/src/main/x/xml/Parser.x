import ecstasy.io.TextPosition;

import impl.*;

/**
 * An XML `Parser`.
 */
class Parser(Boolean ignoreProlog       = False,
             Boolean ignoreComments     = False,
             Boolean ignoreInstructions = False,
            ) {

    /**
     * Default instance of the `Parser`.
     */
    static Parser DEFAULT = new Parser();

    static const Error(TextPosition before, TextPosition after, String desc);

    conditional Document parse(String text, Appender<Error>? errors = Null) {
        TODO
    }

    conditional Document parse(Reader in, Appender<Error>? errors = Null) {
        TODO
    }

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
            return err("Unexpected character: {peek().quoted()}");
        }

        ElementNode elem;
    }

    // -----

    /**
     * TODO
     */
    protected @Unassigned Reader reader;

    /**
     * TODO
     */
    protected Boolean eof;

    /**
     * The next Char
     */
    protected Char ch;

    protected conditional Char peek() {
        return
    }

    protected conditional Char next() {
        Boolean eof = eof;
        Char    ch  = ch;
        if (!ch := reader.next()) {
            eof = True;
            ch  =
        }
    }

    /**
     * TODO
     */
    protected Int pos {
        @Override
        Int get() = reader.offset;

        @Override
        void set(Int n) = reader.reset().skip(n);
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
    protected Boolean match(Char ch) {
        TODO
    }

    /**
     * Log an error.
     *
     * @return `False`
     */
    protected Boolean err(String text) {
        errors.add($"[{lineNumber}:{lineOffset}] ({offset}) {text}")
        return False;
    }
}
