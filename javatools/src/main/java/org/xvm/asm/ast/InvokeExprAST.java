package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.ast.LanguageAST.ExprAST;

import static org.xvm.asm.ast.LanguageAST.NodeType.INVOKE_EXPR;

import static org.xvm.util.Handy.indentLines;
import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Invocation expression.
 */
public class InvokeExprAST<C>
        extends ExprAST<C> {

    private C            method;
    private Object[]     retTypes;
    private ExprAST<C>   target;
    private ExprAST<C>[] args; // can have the tail of nulls for "default" args

    InvokeExprAST() {
    }

    /**
     * Construct an InvokeExprAST.
     */
    public InvokeExprAST(C method, C[] retTypes, ExprAST target, ExprAST<C>[] args) {
        assert method   != null;
        assert retTypes != null && Arrays.stream(retTypes).allMatch(Objects::nonNull);

        this.method   = method;
        this.retTypes = retTypes;
        this.target   = target;
        this.args     = args == null ? NO_EXPRS : args;
    }

    @Override
    public int getCount() {
        return retTypes.length;
    }

    @Override
    public C getType(int i) {
        return (C) retTypes[i];
    }

    @Override
    public NodeType nodeType() {
        return INVOKE_EXPR;
    }

    public ExprAST<C> getTarget() {
        return target;
    }

    public C getMethod() {
        return method;
    }

    public ExprAST<C>[] getArgs() {
        return args; // note: caller must not modify returned array in any way
    }

    @Override
    public void read(DataInput in, ConstantResolver<C> res)
            throws IOException {
        method   = res.getConstant(readMagnitude(in));
        retTypes = readASTArray(in, res);
        target   = deserialize(in, res);
        args     = readExprArray(in, res);
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        method = res.register(method);
        res.registerAll(retTypes);
        target.prepareWrite(res);
        prepareWriteASTArray(res, args);
    }

    @Override
    public void write(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        out.writeByte(nodeType().ordinal());

        writePackedLong(out, res.indexOf(method));
        writeConstArray(out, res, retTypes);
        target.write(out, res);
        writeASTArray(out, res, args);
    }

    @Override
    public String dump() {
        StringBuilder buf = new StringBuilder();
        buf.append(this);
        for (ExprAST child : args) {
            buf.append('\n').append(indentLines(child.dump(), "  "));
        }
        return buf.toString();
    }

    @Override
    public String toString() {
        return nodeType().name() + ":" + retTypes.length + " " + method;
    }
}