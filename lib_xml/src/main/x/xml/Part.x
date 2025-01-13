/**
 * This represents a "part" of an XML [Document]. XML documents have lots of different forms of
 * content, including the obvious (elements and attributes), and the not-so-obvious (comments,
 * processing instructions, etc.)
 */
interface Part {
    /**
     * The [Document] to which this part belongs. It is possible that a `Part` is an orphan, and its
     * `Document` would be `Null`.
     */
    @RO Document? doc;

    /**
     * The root (the parent-most) `Element` of the XML.
     */
    @RO Element root.get() = doc?.root : assert;
// {
//        TODO
//    };

    enum Form<FormType extends Part> {DOC<Document>, ELEM<Element>, ATTR<Attribute>, DATA<Part>, REF<Part>, PI<Part>, }

    /**
     * The [Form] of the `Part`.
     */
    @RO Form form;

    /**
     * The parent `Part` of this `Part`, or `Null`.
     */
    @RO Part? parent;

    /**
     * The sequence of `Part` objects nested within this `Part` object.
     */
    @RO List<Part> parts.get() = [];

    /**
     * A [List] of sibling `Part` objects including this `Part`.
     */
    @RO List<Part> siblings;

    /**
     * This `Part`'s index in its [siblings] list.
     */
    @RO Int index;
}