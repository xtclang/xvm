package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.ast.BiExprAST.Operator;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Comparison over a chain of expressions.
 */
public class CmpChainExprAST<C>
        extends ExprAST<C> {

    private ExprAST<C>[] exprs;
    private Operator[]   ops;

    private transient C booleanType;

    CmpChainExprAST() {}

    public CmpChainExprAST(ExprAST<C>[] exprs, Operator[] ops) {
        assert exprs != null && Arrays.stream(exprs).allMatch(Objects::nonNull);
        assert ops   != null && Arrays.stream(ops).allMatch(Objects::nonNull);
        assert ops.length == exprs.length - 1;

        for (Operator op : ops) {
            assert switch (op) {
                case CompEq, CompNeq, CompLt, CompGt, CompLtEq, CompGtEq
                        -> true;
                default -> false;
            };
        }
        this.exprs = exprs;
        this.ops   = ops;
    }

    public Operator[] getOps() {
        return ops;  // note: caller must not modify returned array in any way
    }

    public ExprAST<C>[] getExprs() {
        return exprs;  // note: caller must not modify returned array in any way
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return booleanType;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.CmpChainExpr;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        int count = readMagnitude(in);

        ExprAST<C>[] exprs = new ExprAST[count];
        for (int i = 0; i < count; ++i) {
            exprs[i] = readExprAST(in, res);
        }
        Operator[] ops = new Operator[count-1];
        for (int i = 0; i < count-1; ++i) {
            ops[i] = Operator.values()[readMagnitude(in)];
        }
        this.exprs  = exprs;
        this.ops    = ops;
        booleanType = res.typeForName("Boolean");
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        prepareASTArray(exprs, res);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        int count = exprs.length;
        writePackedLong(out, count);
        for (ExprAST expr : exprs) {
            expr.writeExpr(out, res);
        }
        for (Operator op : ops) {
            writePackedLong(out, op.ordinal());
        }
    }

    @Override
    public String dump() {
        StringBuilder buf = new StringBuilder();
        for (int i = 0, c = exprs.length; i < c; i++) {
            buf.append(exprs[i].dump());
            if (i < c-1) {
                buf.append(' ')
                   .append(ops[i].text)
                   .append(' ');
            }
        }
        return buf.toString();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        for (int i = 0, c = exprs.length; i < c; i++) {
            buf.append(exprs[i]);
            if (i < c-1) {
                buf.append(' ')
                   .append(ops[i].text)
                   .append(' ');
            }
        }
        return buf.toString();
    }
}