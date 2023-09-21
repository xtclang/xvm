package org.xvm.asm.ast;


import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.constants.TypeConstant;


/**
 * Unpack the underlying tuple into a multi-return.
 */
public class UnpackExprAST
        extends DelegatingExprAST {

    private TypeConstant[] types;

    UnpackExprAST() {}

    public UnpackExprAST(ExprAST expr, TypeConstant[] types) {
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
    public TypeConstant getType(int i) {
        return types[i];
    }

    @Override
    public String toString() {
        return "/*unpack*/ " + getExpr();
    }
}