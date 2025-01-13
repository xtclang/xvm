/**
 * An implementation of [Comment] using the [Node] framework.
 */
class CommentNode
        extends Comment
        incorporates Node {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a `CommentNode` from its constituent pieces.
     *
     * @param parent  the [DocumentNode] or [ElementNode] that will contain this `CommentNode`
     * @param text    the text content of the `Comment`
     */
    construct((DocumentNode|ElementNode)? parent, String text) {
        parent_ = parent;
        construct Comment(text);
    }

    /**
     * Construct a new mutable `CommentNode`, copying the content of the passed
     * `CommentNode`.
     *
     * @param that  the `CommentNode` to copy
     */
    @Override
    construct(CommentNode that) {
        this.text = that.text;
    }

    /**
     * Construct a new `CommentNode`, copying the content of the passed `Comment`.
     *
     * @param that  the `Comment` to copy
     */
    construct(Comment that) {
        construct Comment(that.text);
    }

    // ----- Comment API ---------------------------------------------------------------------------

    @Override
    @RO (DocumentNode|ElementNode)? parent.get() = parent_.as((DocumentNode|ElementNode)?);

    // ----- Node API ------------------------------------------------------------------------------

    @Override
    protected conditional Node allowsChild(Part part) = False;
}