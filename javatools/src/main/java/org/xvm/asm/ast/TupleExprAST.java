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
    public boolean isAssignable() {
        // tuple is used to collect multiple assignable L-Value expressions into an assignable unit
        ExprAST<C>[] values = getValues();
        int count = values.length;
        if (count == 0) {
            return false;
        }
        for (int i = 0; i < count; ++i) {
            if (!values[i].isAssignable()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public NodeType nodeType() {
        return TupleExpr;
    }
}