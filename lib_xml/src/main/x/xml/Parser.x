import ecstasy.io.TextPosition;

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
}
