package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.Constant;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;


/**
 * The Convert expressions.
 */
public class ConvertExprAST
        extends DelegatingExprAST {

    private TypeConstant[] types;
    private Constant[]     convMethods; // nulls are allowed

    ConvertExprAST() {}

    public ConvertExprAST(ExprAST expr, TypeConstant[] types, MethodConstant[] convMethods) {
        super(expr);

        assert types != null && Arrays.stream(types).allMatch(Objects::nonNull);
        assert convMethods != null && convMethods.length <= types.length;

        this.types       = types;
        this.convMethods = convMethods;
    }

    public ConvertExprAST(ExprAST expr, TypeConstant type, MethodConstant convMethod) {
        super(expr);

        assert type != null && convMethod != null;

        this.types       = new TypeConstant[] {type};
        this.convMethods = new MethodConstant[] {convMethod};
    }

    public Constant[] getConvMethods() {
        return convMethods;
    }

    @Override
    public boolean isConditional() {
        return getExpr().isConditional();
    }

    @Override
    public NodeType nodeType() {
        return NodeType.ConvertExpr;
    }

    @Override
    public TypeConstant getType(int i) {
        return types[i];
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        super.readBody(in, res);

        types       = readTypeArray(in, res);
        convMethods = readSparseConstArray(in, res, types.length);
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        super.prepareWrite(res);

        prepareConstArray(types, res);
        prepareConstArray(convMethods, res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        super.writeBody(out, res);

        writeConstArray(types, out, res);
        writeSparseConstArray(convMethods, out, res);
    }

    @Override
    public String toString() {
        if (convMethods.length == 1) {
            MethodConstant convMethod = (MethodConstant) convMethods[0];
            return getExpr().toString() + "." + convMethod.getName() + "()";
        } else {
            StringBuilder buff = new StringBuilder("(");
            String        expr = getExpr().toString();
            boolean       cond = isConditional();
            for (int i = 0, c = convMethods.length; i < c; i++) {
                MethodConstant convMethod = (MethodConstant) convMethods[i];

                if (i > (cond ? 1 : 0)) {
                    buff.append(", ");
                }
                if (convMethod == null) {
                    if (cond && i == 0) {
                        buff.append("conditional ");
                    } else {
                        buff.append(expr)
                            .append("[").append(i).append("]");
                    }
                } else {
                    buff.append(expr)
                        .append("[").append(i).append("].")
                        .append(convMethod.getName())
                        .append('<').append(types[i].getValueString()).append(">()");
                    }
            }
            return buff.append(')').toString();
        }
    }
}