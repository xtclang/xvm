/**
 * An [XML Processing Instruction (PI)](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-PI).
 *
 * As a simple class, a new orphan Processing `Instruction` (PI) can be created, and then added to
 * a [Document] or [Element]. For example:
 *
 *     Instruction pi = new Instruction("test", "details go here");
 *     doc.root.parts.insert(0, pi);
 *
 * Processing instructions are fairly rarely used, but they can appear anywhere in a document
 * (within the document prolog, within the DTD, within any element, and after the root element).
 * They can even appear in the middle of an element's value.
 */
class Instruction(String target, String? text = Null)
        implements Part {

    /**
     * This constructor allows a new orphan Processing `Instruction` (PI) to be created, which can
     * then be added to a [Document] or [Element].
     */
    construct(String target, String? text = Null) {
        assert:arg isValidTarget(target) as $"Invalid Processing Instruction target: {target.quoted()}";
        assert:arg isValidInstruction(text?) as $"Invalid Processing Instruction text: {text.quoted()}";
        this.target = target;
        this.text   = text;
    }

    @Override
    public/protected (Document|Element)? parent.set((Document|Element)? p) {
        // can't orphan the Instruction once it has been adopted
        assert parent == Null;
        super(p);
    }

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