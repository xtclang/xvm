/**
 * An [XML document](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-document) holds a single top
 * element, and may contain other meta-information including an
 * [XMLDecl](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-XMLDecl), a
 * [doctypedecl](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-doctypedecl), and any number of
 * [comments](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-Comment) and
 * [processing instructions](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-PI) aka PIs.
 */
interface Document
        extends Part, Freezable {
    @Override
    @RO Document doc.get() = this;

    /**
     * The root XML [Element] of the `Document`. An XML `Document` always has a single root
     * `Element`. Setting the root `Element` may throw a ReadOnly exception if the document is not
     * mutable, or it may copy the entire `Element` that is provided since the provided `Element`
     * may already be part of a different `Document`.
     */
    @Override
    Element root;

    @Override
    @RO Part? parent.get() = Null;

    /**
     * If this `Document` is already mutable, then return this `Document`, otherwise create a
     * mutable form of this Document and return it.
     *
     * A `Document` instance that `.is(immutable)` can be assumed to be immutable, but the converse
     * is not true; to be sure that a `Document` is mutable, always rely on this method.
     *
     * @return a mutable form of this `Document`, which will be this `Document` if it is already
     *         mutable
     */
    Document ensureMutable();

    /**
     * A `Document` has an optional
     * [XMLDecl](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-XMLDecl), which is structured as an
     * XML PI (processing instruction) using a reserved Target Name of "XML" (case insensitive).
     * The returned [Instruction] must not be mutated directly; instead, modify the [xmlVersion],
     * [encoding], and [standalone] properties on this `Document`.
     */
    @RO Instruction? xmlDecl;

    /**
     * Explicitly initialize the [XMLDecl](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-XMLDecl)
     * portion of the document. If none exists, one is created. If one exists, any previous
     * information is discarded, and only the information passed to this method is retained.
     *
     * @param version     the XML version
     * @param encoding    the XML encoding
     * @param standalone  the XML "standalone" specifier
     *
     * @return the new XMLDecl as a Processing `Instruction`
     *
     * @throws ReadOnly  if the `Document` is not mutable
     */
    Instruction initXmlDecl(Version version = v:1.0, String? encoding = Null, Boolean? standalone = Null);

    /**
     * The optional [XMLDecl](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-XMLDecl) "version"
     * value. At the present time (which is now in the past if you're reading this), the only XML
     * version is `1.0`.
     */
    Version? xmlVersion;

    /**
     * The default XML version number. At the present time (which is now in the past if you're
     * reading this), the only XML version is `1.0`.
     */
    static Version DefaultVersion = v:1.0;

    /**
     * The optional [XMLDecl](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-XMLDecl) "encoding"
     * value.
     */
    String? encoding;

    /**
     * The optional [XMLDecl](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-XMLDecl) "standalone"
     * value.
     */
    Boolean? standalone;

    @Override
    Int estimateStringLength(Boolean pretty = False, Int indent=0) {
        return parts.reduce(0, (sum, part) -> sum + part.estimateStringLength(pretty))
                + (pretty ? parts.size - 1 : 0);
    }

    @Override
    Writer appendTo(Writer buf, Boolean pretty = False, String indent="") {
        Loop: for (Part part : parts) {
            if (pretty && !Loop.first) {
                buf.add('\n');
            }
            part.appendTo(buf, pretty);
        }
        return buf;
    }

    @Override
    static <CompileType extends Document> Int64 hashCode(CompileType value) {
        Iterator<Part> iter = value.parts.iterator();
        Int            hash = iter.next()?.hashCode() : assert; // doc must have at least one part
        while (Part next := iter.next()) {
            hash = hash.rotateLeft(17) ^ next.hashCode();
        }
        return hash;
    }

    @Override
    static <CompileType extends Document> Boolean equals(CompileType value1, CompileType value2) {
        if (value1.xmlVersion != value2.xmlVersion
                || value1.encoding != value2.encoding
                || value1.standalone != value2.standalone
                || value1.root != value2.root) {
            return False;
        }

        List<Part> parts1 = value1.parts;
        List<Part> parts2 = value2.parts;
        if (parts1.size != parts2.size) {
            return False;
        }
        Iterator<Part> iter1 = parts1.iterator();
        Iterator<Part> iter2 = parts2.iterator();
        while ( Part part1 := iter1.next(),
                Part part2 := iter2.next()) {
            if (part1.is(Element)) {
                // already compared the root element
                if (!part2.is(Element)) {
                    return False;
                }
            } else if (part1 != part2) {
                return False;
            }
        }
        return True;
    }
}