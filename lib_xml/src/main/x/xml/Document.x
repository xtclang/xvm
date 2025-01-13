/**
 * An [XML document](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-document) holds a single top
 * element, and may contain other meta-information including an
 * [XMLDecl](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-XMLDecl), a
 * [doctypedecl](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-doctypedecl), and any number of
 * [comments](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-Comment) and
 * [processing instructions](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-PI) aka PIs.
 */
interface Document
        extends Part {
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
     * This is the [Version] number from the
     * [VersionInfo](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-VersionInfo) in the optional
     * [XMLDecl](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-XMLDecl) section. At present, the
     * only XML version is `1.0`.
     */
    Version? xmlVersion;

    /**
     * The XML "standalone" specifier.
     */
    String? encoding;

    /**
     * The XML "standalone" specifier.
     */
    Boolean standalone;

    /**
     * The root XML [Element] of the `Document`. An XML `Document` always has a single root
     * `Element`.
     */
    @Override
    Element root;
}