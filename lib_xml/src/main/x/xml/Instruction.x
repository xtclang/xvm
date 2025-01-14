/**
 * An [XML Processing Instruction (PI)](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-PI).
 */
class Instruction
        implements Part {
    /**
     * The "target" of the Processing `Instruction`.
     */
    String target.set(String s) {
        assert:arg isValidTarget(s) as $"Invalid Processing Instruction target: {s.quoted()}";
        super(s);
    }

    /**
     * The optional text content of the Processing `Instruction`.
     */
    String? text.set(String? s) {
        assert:arg isValidInstruction(s?) as $"Invalid Processing Instruction text: {s.quoted()}";
        super(s);
    }

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