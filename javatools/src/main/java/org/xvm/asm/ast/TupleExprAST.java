package org.xvm.asm.ast;


import static org.xvm.asm.ast.BinaryAST.NodeType.TupleExpr;


/**
 * A Tuple expression that is not a constant is structurally identical to the List expression.
 */
public class TupleExprAST<C>
        extends ListExprAST<C> {

    TupleExprAST() {}

    public TupleExprAST(C type, ExprAST<C>[] values) {
        super(type, values);
    }

    @Override
    public NodeType nodeType() {
        return TupleExpr;
    }
}