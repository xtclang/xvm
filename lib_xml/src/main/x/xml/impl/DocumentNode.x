/**
 * An implementation of the [Document] interface using the [Node] framework. For a given XML
 * document, there's only one [Document] instance, so this implementation does not attempt to
 * provide any special optimizations, either for time or space. It is the only [Part] of the a
 * `Document` that allows a special [Instruction] with the otherwise-illegal target of "xml", and
 * that `Instruction` (if it exists) must be the first `Part` of the `Document`. (The [XmlDeclNode]
 * class is the only valid representation of that `Instruction`.)
 */
class DocumentNode(xml.Element root)
        implements Document
        incorporates Node {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct an empty `DocumentNode` that contains an empty [ElementNode] of the specified name.
     *
     * @param name  the name of the root [Element]
     */
    construct(String name) {
        val node = new ElementNode(name);
        root   = node;
        child_ = node;
    } finally {
        root.as(Node).parent_ = this;
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
                root = node;
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