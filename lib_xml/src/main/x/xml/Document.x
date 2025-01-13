/**
 * An [XML document](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-document) holds a single top
 * element, and may contain other meta-information including an
 * [XMLDecl](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-XMLDecl), a
 * [doctypedecl](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-doctypedecl), and any number of
 * [comments](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-Comment) and
 * [processing instructions](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-PI) aka PIs.
 */
interface Document
        extends Freezable {
    /**
     * TODO
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
     * @param version     TODO
     * @param encoding    TODO
     * @param standalone  TODO
     *
     * @return TODO
     */
    Instruction initXmlDecl(Version version = v:1.0, String? encoding = Null, Boolean? standalone = Null);

    /**
     *
     */
    void deleteXmlDecl();

    /**
     * This is the [Version] number from the
     * [VersionInfo](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-VersionInfo) in the optional
     * [XMLDecl](https://www.w3.org/TR/2008/REC-xml-20081126/#NT-XMLDecl) section. At present, the
     * only XML version is `1.0`.
     */
    Version? xmlVersion;

    String? encoding;

    Boolean standalone;

//    @RO List<Content> contents;

    @RO Element root;
}