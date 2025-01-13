/**
 * An implementation of [Instruction] using the [Node] framework.
 */
class InstructionNode
        extends Instruction
        incorporates Node {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a `InstructionNode` from its constituent pieces.
     *
     * @param parent  the [DocumentNode] or [ElementNode] that will contain this `InstructionNode`
     * @param target  the "target" of the Processing `Instruction`
     * @param text    the optional text content of the Processing `Instruction`
     */
    construct((DocumentNode|ElementNode)? parent, String target, String? text = Null) {
        parent_ = parent;
        construct Instruction(target, text);
    }

    /**
     * Construct a new mutable `InstructionNode`, copying the content of the passed
     * `InstructionNode`.
     *
     * @param that  the `InstructionNode` to copy
     */
    @Override
    construct(InstructionNode that) {
        this.target = that.target;
        this.text   = that.text;
    }

    /**
     * Construct a new `InstructionNode`, copying the content of the passed `Instruction`.
     *
     * @param that  the `Instruction` to copy
     */
    construct(Instruction that) {
        construct Instruction(that.target, that.text);
    }

    // ----- Instruction API -----------------------------------------------------------------------

    @Override
    @RO (DocumentNode|ElementNode)? parent.get() = parent_.as((DocumentNode|ElementNode)?);

    // ----- Node API ------------------------------------------------------------------------------

    @Override
    protected conditional Node allowsChild(Part part) = False;
}