class DocNode(Element root)
        implements Document
        incorporates Node {

    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a new mutable `DocNode`, copying the content of the passed DocNode.
     *
     * @param that  the `DocNode` to copy
     */
    protected construct(DocNode that) {
        TODO
    }

    // ----- Document API --------------------------------------------------------------------------

    @Override
    Document ensureMutable() = this.is(immutable) ? new DocNode(this) : this;

    @Override
    Instruction? xmlDecl.get() {
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

        // TODO

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
            cachedXmlDecl = Null;
        }
    }

    @Override
    immutable DocNode freeze(Boolean inPlace = False) {
        TODO
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
            extends Instruction {
        construct (Document parent, String target, String? text) {
            // this does not invoke the super constructor, because the target "xml" is explicitly
            // illegal in XML, except in the XMLDecl, so the Instruction constructor will assert
            assert:arg ecstasy.collections.CaseInsensitive.areEqual(target, "xml");
            this.parent = parent;
            this.target = target;
            this.text   = text;
        }

        @Override
        String target.set(String s) = throw new ReadOnly();

        @Override
        String? text.set(String? s) = throw new ReadOnly();

        @Override
        Document? parent;

        @Override
        @RO Int index.get() = 0;

        @Override
        void delete() {
            if (Document parent ?= parent) {
                parent.xmlVersion = Null;
                parent.encoding   = Null;
                parent.standalone = Null;
                this.parent = Null;
            }
        }

        @Override
        immutable XmlDecl freeze(Boolean inPlace = False) {
            TODO
        }
    }
}