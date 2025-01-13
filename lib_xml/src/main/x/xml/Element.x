import ecstasy.io.TextPosition;

/**
 * Represents a piece of an XML document.
 */
interface Element
        extends Part {
    /**
     * `Element`s can be nested. A nested `Element` will have a non-`Null` parent `Element`, or be a
     * child of the [Document] itself.
     */
    @Override
    @RO (Element|Document)? parent;

    /**
     * For a `Element` that has a name, such as an [Element] or an [Attribute], this provides the name.
     * Some forms of `Element` do not have a name, such as a [Comment].
     */
    String name;

    /**
     * The textual form of the `Element`'s value, or `Null` if there is no value.
     */
    String? value;

    /**
     * A representation of the [Attribute]s of this XML `Element`.
     */
    @RO Map<String, Attribute> attributes;

    /**
     * A representation of the child `Element`s of this XML `Element`.
     */
    @RO List<Element> elements;

    Element ensureElement(String name);
}