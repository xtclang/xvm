/**
 * An [XML Comment](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-Comment).
 */
class Comment
        implements Part {
    /**
     * The text content of the `Comment`.
     */
    String text;

    @Override
    Int estimateStringLength(Boolean pretty = False) {
        return 8 + (text.empty ? 0 : text.size + 1);
    }

    @Override
    Writer appendTo(Writer buf, Boolean pretty = False) {
        "<!--".appendTo(buf);
        if (!text.empty) {
            buf.add(' ');
            text.appendTo(buf);
        }
        return " -->".appendTo(buf);
    }
}