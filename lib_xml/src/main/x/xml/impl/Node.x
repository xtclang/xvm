/**
 * Represents internal details about a [Part] of an XML [Document].
 */
mixin Node
        into Part {
    /**
     * `Node`s can be nested. A nested `Node` will have a non-`Null` parent `Node`.
     */
    protected Node!? parent_ = Null;

    /**
     * The next sibling `Node` after this `Node`, or `Null` if none.
     */
    protected Node!? next_ = Null;

    /**
     * The first `Node` that is nested under this `Node`, or `Null` if none.
     */
    protected Node!? child_ = Null;

    /**
     * For a `Node` that is the result of parsing an XML document, this is the offset of the `Node`
     * within the original document.
     */
    protected UInt32 offset_ = 0;

    /**
     * For a `Node` that is the result of parsing an XML document, this is the length of the `Node`
     * within the original document. For some implementations of `Node`, it is expected that this
     * may be calculated on demand.
     */
    protected UInt32 length_ = 0;
}