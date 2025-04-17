/**
 * An implementation of the [Document] interface using the [Node] framework. For a given XML
 * document, there's only one [Document] instance, so this implementation does not attempt to
 * provide any special optimizations, either for time or space. It is the only [Part] of the a
 * `Document` that allows a special [Instruction] with the otherwise-illegal target of "xml", and
 * that `Instruction` (if it exists) must be the first `Part` of the `Document`. (The [XmlDeclNode]
 * class is the only valid representation of that `Instruction`.)
 */
class DocumentNode(ElementNode root)
        implements Document
        incorporates Node {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an empty `DocumentNode` that contains an empty [ElementNode] of the specified name.
     *
     * @param name  the name of the root [Element]
     */
    construct(String name, String? value = Null) {
        root = new ElementNode(name, value);
    } finally {
        link_(Null, root);
    }

    /**
     * Construct a new mutable `DocumentNode`, copying the content of the passed `DocumentNode`.
     *
     * @param that  the `DocumentNode` to copy
     */
    @Override
    construct(DocumentNode that) {
        this.xmlVersion = that.xmlVersion;
        this.encoding   = that.encoding;
        this.standalone = that.standalone;

        Node? prev = Null;
        for (Part part : that.parts) {
            Node node = makeNode(part);
            if (prev == Null) {
                child_ = node;
            } else {
                prev.next_ = node;
            }
            if (node.is(ElementNode)) {
                assert !&root.assigned;
                root = node;
            }
            prev = node;
        }
        assert &root.assigned;
    } finally {
        // finish the adoption
        for (Node? node = child_; node != Null; node = node.next_) {
            node.parent_ = this;
        }
    }

    /**
     * Construct a new `DocumentNode`, copying the content of the passed `Document`.
     *
     * @param that  the `Document` to copy
     */
    construct(Document that) {
        Node? prev = Null;
        EachPart: for (Part part : that.parts) {
            Node node = makeNode(part);
            assert:arg !node.is(XmlDeclNode) || EachPart.first as "The XMLDecl must occur at the start of the document";
            if (node.is(xml.Element)) {
                assert:arg !&root.assigned as "An XML document only contains one root element";
                root = node.as(ElementNode);
            }
            if (prev == Null) {
                child_ = node;
            } else {
                prev.next_ = node;
            }
            prev = node;
        }
        assert:arg &root.assigned as "An XML document must contain a root element";
    } finally {
        // finish the adoption
        for (Node? node = child_; node != Null; node = node.next_) {
            node.parent_ = this;
        }
    }

    /**
     * Create a `DocumentNode` that contains the children provided in the passed linked list of
     * parsed nodes.
     *
     * @param firstNode  the first [Parsed] [Node] in the linked list of nodes
     */
    construct(Parsed firstNode) {
        for (Node? node = firstNode; node != Null; node = node.next_) {
            if (node.is(XmlDeclNode)) {
                assert this.xmlVersion == Null;
                (this.xmlVersion, this.encoding, this.standalone) = node.settings();
            } else if (node.is(ElementNode)) {
                assert !&root.assigned;
                root = node;
            }
        }
    } finally {
        for (Node? node = firstNode; node != Null; node = node.next_) {
            node.parent_ = this;
        }
        this.child_  = firstNode;
    }

    // ----- Document API --------------------------------------------------------------------------

    @Override
    Document ensureMutable() = this.is(immutable) ? new DocumentNode(this) : this;

    @Override
    @RO Instruction? xmlDecl.get() = child_.is(XmlDeclNode) ?: Null;

    @Override
    Instruction initXmlDecl(Version version = v:1.0, String? encoding = Null, Boolean? standalone = Null) {
        this.xmlVersion = version;
        this.encoding   = encoding;
        this.standalone = standalone;
        return makeXmlDecl() ?: assert;
    }

    @Override
    Version? xmlVersion.set(Version? newVer) {
        Version? oldVer = xmlVersion;
        if (newVer != oldVer) {
            // we could check the version to make sure it's valid, which assumes no new versions
            // will ever be introduced, but instead we allow the caller to shoot themselves in the
            // foot with some non-existent XML version if they really want to
            super(newVer);
            makeXmlDecl();
            mod();
        }
    }

    @Override
    String? encoding.set(String? newEnc) {
        String? oldEnc = encoding;
        if (newEnc != oldEnc) {
            assert:arg isValidEncoding(newEnc?);
            super(newEnc);
            makeXmlDecl();
            mod();
        }
    }

    @Override
    Boolean? standalone.set(Boolean? newSA) {
        Boolean? oldSA = standalone;
        if (newSA != oldSA) {
            super(newSA);
            makeXmlDecl();
            mod();
        }
    }

    @Override
    immutable DocumentNode freeze(Boolean inPlace = False) {
        if (this.is(immutable)) {
            return this;
        }

        if (!inPlace) {
            // copy the XML document
            return new DocumentNode(this).freeze(inPlace=True);
        }

        prepareFreeze();
        return makeImmutable();
    }

    // ----- Node API ------------------------------------------------------------------------------

    @Override
    Node clear() {
        assert as "A Document cannot clear its contents; doing so would delete the root Element";
    }

    @Override
    protected conditional Node allowsChild(Part part) {
        if (Node node := super(part)) {
            return switch (node.is(_)) {
                case XmlDeclNode:
                case ElementNode:
                case InstructionNode:
                case CommentNode:    (True, node);
                default:             False;
            };
        }
        return False;
    }

    @Override
    protected (Node cur, UInt32 mods) replaceNode(Int index, Node? prev, Node? cur, Part part) {
        assert:bounds cur != Null;
        assert:arg Node node := allowsChild(part) as "Type not allowed: {&part.actualType}";

        switch (cur.is(_), node.is(_)) {
        case (ElementNode, ElementNode):
            (Node result, UInt32 mods) = super(index, prev, cur, part);
            root = result.as(ElementNode);
            return result, mods;
        case (ElementNode, _):
            assert as "The root Element can not be deleted";
        case (_, ElementNode):
            assert as "Only one root Element is permitted on a Document";
        case (_, XmlDeclNode):
            assert:bounds index == 0 as "XMLDecl must occur at the very start of the XML Document";
            (Version xmlVersion, String? encoding, Boolean? standalone) = node.settings();
            if (Node oldNode ?= child_, !oldNode.is(XmlDeclNode)) {
                // we're replacing a node that is NOT an XmlDeclNode, so just delete the old node
                deleteNode(0, Null, oldNode);
            }
            // setting these properties will have the side effect of creating a new (or modifying an
            // existing) XmlDeclNode
            this.xmlVersion = xmlVersion;
            this.encoding   = encoding;
            this.standalone = standalone;
            return child_.as(XmlDeclNode), mods_;
        case (XmlDeclNode, _):
            assert index == 0 && prev == Null;
            deleteNode(0, Null, cur);
            return insertNode(0, Null, child_, part);
        case (_, InstructionNode):
        case (_, CommentNode):
            return super(index, prev, cur, part);
        default:
            assert as $"Unsupported replacement from {&cur.actualClass} to {&node.actualClass}";
        }
    }

    @Override
    protected (Node cur, UInt32 mods) insertNode(Int index, Node? prev, Node? cur, Part part) {
        assert:arg Node node := allowsChild(part) as "Type not allowed: {&part.actualType}";
        switch (node.is(_)) {
        case ElementNode:
            assert as "Only one root Element is permitted on a Document";
        case XmlDeclNode:
            assert:bounds index == 0 as "XMLDecl must occur at the very start of the XML Document";
            (Version xmlVersion, String? encoding, Boolean? standalone) = node.settings();
            this.xmlVersion = xmlVersion;
            this.encoding   = encoding;
            this.standalone = standalone;
            return child_.as(XmlDeclNode), mods_;
        default:
            return super(index, prev, cur, part);
        }
    }

    @Override
    protected (Node? cur, UInt32 mods) deleteNode(Int index, Node? prev, Node cur) {
        switch (cur.is(_)) {
        case Null:
            assert:bounds as $"No Node exists at index {index}";
        case XmlDeclNode:
            // to delete the XMLDecl, just clear the properties responsible for it existing
            this.xmlVersion = Null;
            this.encoding   = Null;
            this.standalone = Null;
            return child_, mods_;
        case ElementNode:
            assert as "The root Element can not be deleted";
        case InstructionNode:
        case CommentNode:
            return super(index, prev, cur);
        default:
            assert as $"Unsupported node: {&cur.actualClass}";
        }
    }

    // ----- internal ------------------------------------------------------------------------------

    protected XmlDeclNode? makeXmlDecl() {
        // discard any previous XmlDeclNode
        if (XmlDeclNode xmlDecl := child_.is(XmlDeclNode)) {
            unlink_(Null, xmlDecl);
        }

        // if there's no information to include in a new XmlDeclNode, then don't create one
        if (xmlVersion == Null && encoding == Null && standalone == Null) {
            return Null;
        }

        XmlDeclNode xmlDecl = new XmlDeclNode(this, xmlVersion, encoding, standalone);
        link_(Null, xmlDecl);
        return xmlDecl;
    }
}