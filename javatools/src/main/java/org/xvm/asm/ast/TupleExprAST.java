package org.xvm.asm.ast;


import org.xvm.asm.constants.TypeConstant;

import static org.xvm.asm.ast.BinaryAST.NodeType.TupleExpr;


/**
 * A Tuple expression that is not a constant is structurally identical to the List expression.
 */
public class TupleExprAST
        extends ListExprAST {

    TupleExprAST() {}

    public TupleExprAST(TypeConstant type, ExprAST[] values) {
        super(type, values);
    }

    @Override
    public NodeType nodeType() {
        return TupleExpr;
    }

    @Override
    public boolean isAssignable() {
        // tuple is used to collect multiple assignable L-Value expressions into an assignable unit
        ExprAST[] values = getValues();
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
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append('(');
        for (ExprAST value : getValues()) {
            buf.append(value.toString()).append(", ");
        }
        buf.delete(buf.length()-2, buf.length())
           .append(')');
        return buf.toString();
    }
}