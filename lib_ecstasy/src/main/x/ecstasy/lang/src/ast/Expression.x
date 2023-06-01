/**
 * This is the abstract root of all AST expression nodes.
 */
@Abstract const Expression
        extends Node {
    /**
     * Determine if the expression has a compile-time constant value.
     *
     * @return True iff the expression evaluates to a compile-time constant
     * @return (conditional) the constant value
     */
    conditional immutable Const hasConstantValue() {
        return False;
    }
}
