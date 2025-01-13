import ecstasy.io.TextPosition;

/**
 * Represents a piece of an XML document.
 */
interface Element {
    /**
     * `Element`s can be nested. A nested `Element` will have a non-`Null` parent `Element`.
     */
    @RO Element? parent;

    /**
     * `Element`s can be nested, and this value indicates the level of nesting of this `Element`;
     * the root `Element` is a [Document], with `parent == Null` and the depth of `0`.
     */
    @RO Int depth;

    /**
     * A [List] of sibling `Element` objects including this `Element`.
     */
    @RO List<Element> siblings;

    /**
     * This `Element`'s index in its [siblings] list.
     */
    @RO Int index;

    /**
     * The first `Element` that is nested under this `Element`, or `Null` if none.
     */
    @RO Element? child;

    /**
     * A [List] of sibling `Element` objects including this `Element`.
     */
    @RO List<Element> children;

    /**
     * For a `Element` that has a name, such as an [Element] or an [Attribute], this provides the name.
     * Some forms of `Element` do not have a name, such as a [Comment].
     */
    @RO String? name;

    /**
     * The textual form of the `Element`'s value, or `Null` if there is no value.
     */
    @RO String? value;

    Element ensureMutable();

    /**
     * For a `Element` that is the result of parsing an XML document, this is the offset of the `Element`
     * within the original document.
     */
    @RO Int? offset;

    /**
     * For a `Element` that is the result of parsing an XML document, this is the length of the `Element`
     * within the original document. For some implementations of `Element`, it is expected that this
     * may be calculated on demand.
     */
    @RO Int? length;

    /**
     * For a `Element` that is the result of parsing an XML document, this is the [TextPosition] of the
     * `Element` within the original document. For some implementations of `Element`, it is expected that
     * this representation would be created -- and as necessary, calculated -- on demand.
     */
    @RO TextPosition? start;

    /**
     * For a `Element` that is the result of parsing an XML document, this is the [TextPosition]
     * immediately following the `Element` within the original document. For some implementations of
     * `Element`, it is expected that this representation would be created -- and as necessary,
     * calculated -- on demand.
     */
    @RO TextPosition? end;

    /**
     * @param indent
     */
    Writer appendTo(Writer writer, String? indent = Null);
}