/**
 * An [XML Processing Instruction (PI)](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-PI).
 */
interface Instruction
        extends Part {
    /**
     * The "target" of the Processing `Instruction`.
     */
    String target;

    /**
     * The optional text content of the Processing `Instruction`.
     */
    String? text;

    @Override
    Int estimateStringLength(Boolean pretty = False) {
        return 4 + target.size + (text?.size + 1 : 0);
    }

    @Override
    Appender<Char> appendTo(Appender<Char> buf, Boolean pretty = False) {
        "<?".appendTo(buf);
        target.appendTo(buf);
        if (text != Null) {
            buf.add(' ');
            text.appendTo(buf);
        }
        return "?>".appendTo(buf);
    }
}