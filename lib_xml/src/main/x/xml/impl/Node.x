import ecstasy.io.TextPosition;

/**
 * Represents a piece of an XML document.
 */
@Abstract class Node {

    enum Form<FormType extends Node> {DOC<Document>, ELEM<Element>, ATTR<Attribute>, DATA<Node>, CD<Node>, REF<Node>, PROLOG<Node>, PI<Node>, }

    /**
     * The [Form] of data that the [Node] represents.
     */
    @RO Form form;

    /**
     * `Node`s can be nested. A nested `Node` will have a non-`Null` parent `Node`.
     */
    @RO Node? parent;

    /**
     * `Node`s can be nested, and this value indicates the level of nesting of this `Node`; a root
     * `Node` (one with `parent == Null`) has a depth of `0`.
     */
    @RO Int depth;

    /**
     * The next `Node` that is nested under this `Node`'s parent, or `Null` if none.
     */
    @RO Node? sibling;

    /**
     * A [List] of sibling `Node` objects including this `Node`.
     */
    @RO List<Node> siblings;

    /**
     * This `Node`'s index in its [siblings] list.
     */
    @RO Int index;

    /**
     * The first `Node` that is nested under this `Node`, or `Null` if none.
     */
    @RO Node? child;

    /**
     * A [List] of sibling `Node` objects including this `Node`.
     */
    @RO List<Node> children;

    /**
     * For a `Node` that has a name, such as an [Element] or an [Attribute], this provides the name.
     * Some forms of `Node` do not have a name, such as a [Comment].
     */
    @RO String? name;

    /**
     * The textual form of the `Node`'s value, or `Null` if there is no value.
     */
    @RO String? value;

    Node ensureMutable();

    /**
     * For a `Node` that is the result of parsing an XML document, this is the offset of the `Node`
     * within the original document.
     */
    @RO Int? offset;

    /**
     * For a `Node` that is the result of parsing an XML document, this is the length of the `Node`
     * within the original document. For some implementations of `Node`, it is expected that this
     * may be calculated on demand.
     */
    @RO Int? length;

    /**
     * For a `Node` that is the result of parsing an XML document, this is the [TextPosition] of the
     * `Node` within the original document. For some implementations of `Node`, it is expected that
     * this representation would be created -- and as necessary, calculated -- on demand.
     */
    @RO TextPosition? start;

    /**
     * For a `Node` that is the result of parsing an XML document, this is the [TextPosition]
     * immediately following the `Node` within the original document. For some implementations of
     * `Node`, it is expected that this representation would be created -- and as necessary,
     * calculated -- on demand.
     */
    @RO TextPosition? end;

    /**
     * @param indent
     */
    Writer appendTo(Writer writer, String? indent = Null);
}