package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.IOException;


/**
 * An expressions that follow the pattern "expression operator expression" and produces a "Boolean"
 * or "Ordered" result.
 */
public class CondOpExprAST<C>
        extends BiExprAST<C> {

    private transient C type;

    CondOpExprAST() {}

    public CondOpExprAST(ExprAST<C> expr1, Operator op, ExprAST<C> expr2) {
        super(expr1, op, expr2);

        assert switch (op) {
            case CondOr, CondXor, CondAnd, CompEq, CompNeq,
                 CompLt, CompGt, CompLtEq, CompGtEq, CompOrd
                    -> true;
            default -> false;
            };
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return type;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.CondOpExpr;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        super.readBody(in, res);

        type = switch (getOp())
            {
            case CondOr, CondXor, CondAnd, CompEq, CompNeq ->
                res.typeForName("Boolean");

            case CompLt, CompGt, CompLtEq, CompGtEq, CompOrd ->
                res.typeForName("Ordered");

            default -> throw new IllegalStateException();
            };
    }
}