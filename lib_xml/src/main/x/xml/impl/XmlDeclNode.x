/**
 * A special [Instruction] used for the XMLDecl portion of the XML document. An instance of this
 * class is, for all effective purposes, read-only -- primarily because its state represents
 * information from (and owned by) a DocumentNode.
 */
class XmlDeclNode
        extends Instruction
        incorporates Node {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an `XmlDeclNode` as if it were a Processing [Instruction].
     *
     * @param parent  the [DocumentNode] that will contain this `XmlDeclNode`
     * @param target  must be "xml" (case-insensitive)
     * @param text    the contents of the Processing Instruction, as defined by XMLDecl
     */
    construct(DocumentNode parent, String target, String? text = Null) {
        // this does not invoke the super constructor, because the target "xml" is explicitly
        // illegal in XML, except in the XMLDecl, so the Instruction constructor will assert
        assert:arg equalsCaseInsens(target, "xml");
        assert:arg isValidInstruction(text?) as $"Invalid XMLDecl text: {text.quoted()}";
        this.parent_ = parent;
        this.target  = target;
        this.text    = text;
    }

    /**
     * Construct an `XmlDeclNode` from the individual pieces of data that it represents.
     *
     * @param parent      the [DocumentNode] that will contain this `XmlDeclNode`
     * @param version     the XML version
     * @param encoding    the XML encoding
     * @param standalone  the XML "standalone" specifier
     */
    construct(DocumentNode parent, Version? xmlVersion, String? encoding, Boolean? standalone) {
        StringBuffer buf = new StringBuffer();
        "version=\"".appendTo(buf);
        // TODO GG:  (xmlVersion ?: "1.0").appendTo(buf);
        if (xmlVersion != Null) {
            xmlVersion.appendTo(buf);
        } else {
            "1.0".appendTo(buf);
        }
        buf.add('\"');

        if (encoding != Null) {
            " encoding=\"".appendTo(buf);
            encoding.appendTo(buf);
            buf.add('\"');
        }

        if (standalone != Null) {
            " standalone=\"".appendTo(buf);
            (standalone ? "yes" : "no").appendTo(buf);
            buf.add('\"');
        }

        construct XmlDeclNode(parent, "xml", buf.toString());
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