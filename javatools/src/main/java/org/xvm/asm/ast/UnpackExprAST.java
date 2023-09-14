package org.xvm.asm.ast;


import java.util.Arrays;
import java.util.Objects;

/**
 * Unpack the underlying tuple into a multi-return.
 */
public class UnpackExprAST<C>
        extends DelegatingExprAST<C> {

    private Object[] types;

    UnpackExprAST() {}

    public UnpackExprAST(ExprAST<C> expr, C[] types) {
        super(expr);

        assert types != null && Arrays.stream(types).allMatch(Objects::nonNull);
        this.types = types;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.UnpackExpr;
    }

    @Override
    public int getCount() {
        return types.length;
    }

    @Override
    public C getType(int i) {
        return (C) types[i];
    }

    @Override
    public String dump() {
        return "Unpack(" + getExpr().dump() + ')';
    }

    @Override
    public String toString() {
        return "Unpack(" + getExpr() + ')';
    }
}