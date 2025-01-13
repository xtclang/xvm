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
    construct(DocumentNode? parent, String target, String text) {
        // this does not invoke the super constructor, because the target "xml" is explicitly
        // illegal in XML, except in the XMLDecl, so the Instruction constructor will assert
        assert:arg equalsCaseInsens(target, "xml");
        assert:arg isValidInstruction(text) as $"Invalid XMLDecl text: {text.quoted()}";
        this.parent_ = parent;
        this.target  = target;
        this.text    = text;
    }

    /**
     * Construct an `XmlDeclNode` from the individual pieces of data that it represents.
     *
     * @param parent      the [DocumentNode] that will contain this `XmlDeclNode`
     * @param xmlVersion  the XML version
     * @param encoding    the XML encoding
     * @param standalone  the XML "standalone" specifier
     */
    construct(DocumentNode? parent, Version? xmlVersion, String? encoding, Boolean? standalone) {
        StringBuffer buf = new StringBuffer();
        "version=\"".appendTo(buf);
        (xmlVersion ?: "1.0").appendTo(buf);
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
    @RO DocumentNode? parent.get() = parent_.as(DocumentNode?);

    @Override
    String target.set(String s) = throw new ReadOnly();     // no direct edits to the XMLDecl

    @Override
    String text.set(String s) = throw new ReadOnly();       // no direct edits to the XMLDecl

    @Override
    void delete() {
        // the XMLDecl gets discarded if it contains no information, so clear the information
        if (Document parent ?= parent) {
            parent.xmlVersion = Null;
            parent.encoding   = Null;
            parent.standalone = Null;
        }
    }

    // ----- Node API ------------------------------------------------------------------------------

    @Override
    protected conditional Node allowsChild(Part part) = False;

    // ----- helpers -------------------------------------------------------------------------------

    /**
     * Obtain the constituent information from the "XMLDecl".
     *
     * @return xmlVersion  the "VersionInfo" version of the XML specification for the Document
     * @return encoding    the "EncodingDecl" specifying the Document encoding used, if any
     * @return standalone  the "SDDecl" specifying the "standalone" indicator, if any
     */
    (Version xmlVersion, String? encoding, Boolean? standalone) settings() {
        if (DocumentNode doc ?= parent) {
            return doc.xmlVersion ?: assert, doc.encoding, doc.standalone;
        }

        assert (Version? xmlVersion, String? encoding, Boolean? standalone) := parse(text);
        return xmlVersion, encoding, standalone;
    }

    /**
     * Parse the XMLDecl string into its constituent members.
     *
     * @param text  the "XMLDecl" text
     *
     * @return `True` iff the XMLDecl string was parseable
     * @return (conditional) the "VersionInfo" version of the XML specification for the Document
     * @return (conditional) the "EncodingDecl" specifying the Document encoding used, if any
     * @return (conditional) the "SDDecl" specifying the "standalone" indicator, if any
     */
    static conditional (Version xmlVersion, String? encoding, Boolean? standalone) parse(String text) {
        Version? xmlVersion = Null;
        String?  encoding   = Null;
        Boolean? standalone = Null;
        String?  key        = Null;
        NextToken: for (String token : text.map(ch -> ch.isWhitespace() || ch == '=' ? ' ' : ch).split(' ')) {
            switch (token) {
            case "version":
            case "encoding":
            case "standalone":
                if (key != Null) {
                    return False;
                }
                key = token;
                break;
            default:
                if (key == Null) {
                    return False;
                }
                switch (key) {
                case "":
                    continue NextToken;
                case "version":
                    if (xmlVersion != Null || encoding != Null || standalone != Null) {
                        return False;
                    }
                    if (!(token := token.unquote(ch -> ch == '\'' || ch == '\"'))) {
                        return False;
                    }
                    try {
                        xmlVersion = new Version(token);
                    } catch (Exception e) {
                        return False;
                    }
                    break;
                case "encoding":
                    if (xmlVersion == Null || encoding != Null || standalone != Null) {
                        return False;
                    } else if (!(encoding := token.unquote(ch -> ch == '\'' || ch == '\"'))) {
                        return False;
                    }
                    break;
                case "standalone":
                    if (xmlVersion == Null || standalone != Null) {
                        return False;
                    } else if (token := token.unquote(ch -> ch == '\'' || ch == '\"')) {
                        standalone = token == "yes";
                    } else {
                        return False;
                    }
                    break;
                default:
                    return False;
                }
                key = Null;
                break;
            }
        }
        return xmlVersion == Null
                ? False
                : (True, xmlVersion, encoding, standalone);
    }
}