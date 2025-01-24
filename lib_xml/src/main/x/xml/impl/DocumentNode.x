class DocumentNode(Element root)
        implements Document
        incorporates Node {

    // ----- constructors --------------------------------------------------------------------------

    /**
     * TODO
     */
    construct(Element root) {
        root   = makeNode(root);
        child_ = root;
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
        EachPart: for (Part part : that.parts) {
            Node node = makeNode(part);
            assert:arg !node.is(XmlDeclNode) || EachPart.first as "The XMLDecl must occur at the start of the document";
            if (node.is(xml.Element)) {
                assert:arg !&root.assigned as "An XML document only contains one root element";
                root = node;
            }
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
        }
    }

    @Override
    String? encoding.set(String? newEnc) {
        String? oldEnc = encoding;
        if (newEnc != oldEnc) {
            assert:arg isValidEncoding(newEnc?);
            super(newEnc);
            makeXmlDecl();
        }
    }

    @Override
    Boolean? standalone.set(Boolean? newSA) {
        Boolean? oldSA = standalone;
        if (newSA != oldSA) {
            super(newSA);
            makeXmlDecl();
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

    XmlDeclNode? makeXmlDecl() {
        // discard any previous XmlDeclNode
        if (child_.is(XmlDeclNode)) {
            child_.parent_ = Null;
            child_ = child_.next_;
        }

        if (xmlVersion == Null && encoding == Null && standalone == Null) {
            return Null;
        }

        StringBuffer buf = new StringBuffer();
        "version=\"".appendTo(buf);
        // TODO GG:  (xmlVersion ?: "1.0").appendTo(buf);
        if (Version ver ?= xmlVersion) {
            ver.appendTo(buf);
        } else {
            "1.0".appendTo(buf);
        }
        buf.add('\"');

        if (String enc ?= encoding) {
            " encoding=\"".appendTo(buf);
            enc.appendTo(buf);
            buf.add('\"');
        }

        if (Boolean sa ?= standalone) {
            " standalone=\"".appendTo(buf);
            (sa ? "yes" : "no").appendTo(buf);
            buf.add('\"');
        }

        XmlDeclNode xmlDecl = new XmlDeclNode(this, "xml", buf.toString());
        return xmlDecl;
    }
}