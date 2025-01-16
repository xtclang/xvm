/**
 * Represents internal details about a [Part] of an XML [Document].
 */
mixin Node
        into Part {
    /**
     * `Node`s can be nested. A nested `Node` will have a non-`Null` parent `Node`.
     */
    Node!? parent_;

    /**
     * The next sibling `Node` after this `Node`, or `Null` if none.
     */
    Node!? next_;

    /**
     * The first `Node` that is nested under this `Node`, or `Null` if none.
     */
    Node!? child_;

    /**
     * For a `Node` that is the result of parsing an XML document, this is the offset of the `Node`
     * within the original document.
     */
    UInt32 offset_;

    /**
     * For a `Node` that is the result of parsing an XML document, this is the length of the `Node`
     * within the original document. For some implementations of `Node`, it is expected that this
     * may be calculated on demand.
     */
    UInt32 length_;
}