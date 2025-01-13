/**
 * An [XML Comment](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-Comment).
 */
interface Comment
        extends Part {
    /**
     * The text content of the `Comment`.
     */
    String text;

    @Override
    Int estimateStringLength(Boolean pretty = False) {
        return 8 + (text.empty ? 0 : text.size + 1);
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf, Boolean pretty = False) {
        "<!--".appendTo(buf);
        buf.add(' ');
        if (!text.empty) {
            text.appendTo(buf);
            buf.add(' ');
        }
        return "-->".appendTo(buf);
    }
}