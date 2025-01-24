/**
 * A special [Instruction] used for the XMLDecl portion of the XML document.
 */
class XmlDeclNode(String target, String? text = Null)
        extends Instruction
        incorporates Node {
    // ----- constructors --------------------------------------------------------------------------

    construct(String target, String? text) {
        // this does not invoke the super constructor, because the target "xml" is explicitly
        // illegal in XML, except in the XMLDecl, so the Instruction constructor will assert
        assert:arg ecstasy.collections.CaseInsensitive.areEqual(target, "xml");
        assert:arg isValidInstruction(text?) as $"Invalid XMLDecl text: {text.quoted()}";
        this.target = target;
        this.text   = text;
    }

    /**
     * Construct a new mutable `XmlDeclNode`, copying the content of the passed `XmlDeclNode`.
     *
     * @param that  the `XmlDeclNode` to copy
     */
    @Override
    construct(XmlDeclNode that) {
        this.target = that.target;
        this.text   = that.text;
    }

    /**
     * Construct a new `InstructionNode`, copying the content of the passed `Instruction`.
     *
     * @param that  the `Instruction` to copy
     */
    construct(Instruction that) {
        construct Instruction(that.target, that.text);
    }

    // ----- Instruction API -----------------------------------------------------------------------

    @Override
    @RO Document? parent.get() = parent_.as(Document?);

    @Override
    String target.set(String s) = throw new ReadOnly();     // no direct edits to the XMLDecl

    @Override
    String? text.set(String? s) = throw new ReadOnly();     // no direct edits to the XMLDecl

    @Override
    void delete() {
        // the XMLDecl gets discarded if it contains no information, so clear the information
        if (Document parent ?= parent) {
            parent.xmlVersion = Null;
            parent.encoding   = Null;
            parent.standalone = Null;
        }
    }
}