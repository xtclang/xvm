package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.constants.TypeConstant;


/**
 * Base class for Invoke, Call and Construct nodes.
 */
public abstract class CallableExprAST
        extends ExprAST {

    private TypeConstant[] retTypes;
    private ExprAST[]      args;

    CallableExprAST() {
    }

    /**
     * Construct an CallableExprAST.
     */
    protected CallableExprAST(TypeConstant[] retTypes, ExprAST[] args) {
        assert retTypes != null && Arrays.stream(retTypes).allMatch(Objects::nonNull);
        assert args     == null || Arrays.stream(args).allMatch(Objects::nonNull);

        this.retTypes = retTypes;
        this.args     = args == null ? NO_EXPRS : args;
    }

    @Override
    public int getCount() {
        return retTypes.length;
    }

    @Override
    public TypeConstant getType(int i) {
        return retTypes[i];
    }

    @Override
    public abstract NodeType nodeType();

    public ExprAST[] getArgs() {
        return args; // note: caller must not modify returned array in any way
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        retTypes = readTypeArray(in, res);
        args     = readExprArray(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        prepareConstArray(retTypes, res);
        prepareASTArray(args, res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        writeConstArray(retTypes, out, res);
        writeExprArray(args, out, res);
    }

    @Override
    public String toString() {
        if (args == null || args.length == 0) {
            return "()";
        }

        StringBuilder buf = new StringBuilder();
        buf.append('(');
        for (ExprAST arg : args) {
            buf.append(arg)
               .append(", ");
        }
        return buf.delete(buf.length()-2, buf.length())
                  .append(')')
                  .toString();
    }
}