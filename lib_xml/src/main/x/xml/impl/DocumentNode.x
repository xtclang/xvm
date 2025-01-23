class DocumentNode(Element root)
        implements Document
        incorporates Node {

    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a new mutable `DocumentNode`, copying the content of the passed DocumentNode.
     *
     * @param that  the `DocumentNode` to copy
     */
    @Override
    construct(DocumentNode that) {
        TODO
    }

    construct(Document that) {
        TODO
    }

    // ----- Document API --------------------------------------------------------------------------

    @Override
    Document ensureMutable() = this.is(immutable) ? new DocumentNode(this) : this;

    @Override
    @RO Instruction? xmlDecl.get() {
        return cachedXmlDecl?;
        if (!hasXmlDecl) {
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

        return cachedXmlDecl <- new XmlDecl(this, "xml", buf.toString());
    }

    @Override
    Instruction initXmlDecl(Version version = v:1.0, String? encoding = Null, Boolean? standalone = Null) {
        this.xmlVersion = version;
        this.encoding   = encoding;
        this.standalone = standalone;
        return xmlDecl ?: assert;
    }

    @Override
    Version? xmlVersion.set(Version? newVer) {
        Version? oldVer = xmlVersion;
        if (newVer != oldVer) {
            // we could check the version to make sure it's valid, which assumes no new versions
            // will ever be introduced, but instead we allow the caller to shoot themselves in the
            // foot with some non-existent XML version if they really want to
            super(newVer);
            cachedXmlDecl = Null;
        }
    }

    @Override
    String? encoding.set(String? newEnc) {
        String? oldEnc = encoding;
        if (newEnc != oldEnc) {
            assert:arg isValidEncoding(newEnc?);
            super(newEnc);
            cachedXmlDecl = Null;
        }
    }

    @Override
    Boolean? standalone.set(Boolean? newSA) {
        Boolean? oldSA = standalone;
        if (newSA != oldSA) {
            super(newSA);
            cachedXmlDecl = Null; // TODO wrong need to create it and link it in
        }
    }

    @Override
    List<Part> parts.get() {
        // TODO
        TODO
    }

    @Override
    protected void prepareFreeze() {
        val _ = xmlDecl;
//        val _
        // TODO force list of parts
        super();
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

    /**
     * This property holds a cached [Instruction] representing the document's XMLDecl. The actual
     * data is stored in the [xmlVersion], [encoding], and [standalone] properties, and when any of
     * those properties is modified, the cached XMLDecl must be discarded.
     */
    protected Instruction? cachedXmlDecl;

    /**
     * True iff any of the [xmlVersion], [encoding], and [standalone] properties has a non=`Null`
     * value, which indicates that the XML document must have an XMLDecl section.
     */
    protected Boolean hasXmlDecl.get() = xmlVersion != Null || encoding != Null || standalone != Null;

    /**
     * A special [Instruction] used for the XMLDecl portion of the XML document.
     */
    protected static class XmlDecl
            extends Instruction
            incorporates Node {
        construct(DocumentNode parent, String target, String? text) {
            // this does not invoke the super constructor, because the target "xml" is explicitly
            // illegal in XML, except in the XMLDecl, so the Instruction constructor will assert
            assert:arg ecstasy.collections.CaseInsensitive.areEqual(target, "xml");
            this.parent = parent;
            this.target = target;
            this.text   = text;
        }

        @Override
        construct(XmlDecl that) {
            // TODO
        }

        @Override
        String target.set(String s) = throw new ReadOnly();

        @Override
        String? text.set(String? s) = throw new ReadOnly();

        @Override
        Document? parent;

        @Override
        void delete() {
            if (Document parent ?= parent) {
                parent.xmlVersion = Null;
                parent.encoding   = Null;
                parent.standalone = Null;
                this.parent = Null;
            }
        }
    }
}