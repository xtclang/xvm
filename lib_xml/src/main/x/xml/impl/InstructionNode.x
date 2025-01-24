class InstructionNode(String target, String? text = Null)
        extends Instruction(target, text)
        incorporates Node {

    // ----- constructors --------------------------------------------------------------------------

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
    @RO (Document|Element)? parent.get() = parent_.as((Document|Element)?);
}