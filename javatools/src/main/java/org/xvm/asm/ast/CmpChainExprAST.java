package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.ast.BiExprAST.Operator;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Comparison over a chain of expressions.
 */
public class CmpChainExprAST
        extends ExprAST {

    private ExprAST[]      exprs;
    private Operator[]     ops;
    private MethodConstant method; // the comparison method (same for all pairs)

    private transient TypeConstant booleanType;

    CmpChainExprAST() {}

    public CmpChainExprAST(ExprAST[] exprs, Operator[] ops, MethodConstant method) {
        assert exprs != null && Arrays.stream(exprs).allMatch(Objects::nonNull);
        assert ops   != null && Arrays.stream(ops).allMatch(Objects::nonNull);
        assert ops.length == exprs.length - 1;
        assert method != null;

        for (Operator op : ops) {
            assert switch (op) {
                case CompEq, CompNeq, CompLt, CompGt, CompLtEq, CompGtEq
                        -> true;
                default -> false;
            };
        }
        this.exprs  = exprs;
        this.ops    = ops;
        this.method = method;
    }

    public Operator[] getOps() {
        return ops;  // note: caller must not modify returned array in any way
    }

    public ExprAST[] getExprs() {
        return exprs;  // note: caller must not modify returned array in any way
    }

    public MethodConstant getMethod() {
        return method;
    }

    @Override
    public NodeType nodeType() {
        return NodeType.CmpChainExpr;
    }

    @Override
    public TypeConstant getType(int i) {
        assert i == 0;
        return booleanType;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        int count = readMagnitude(in);

        ExprAST[] exprs = new ExprAST[count];
        for (int i = 0; i < count; ++i) {
            exprs[i] = readExprAST(in, res);
        }
        Operator[] ops = new Operator[count-1];
        for (int i = 0; i < count-1; ++i) {
            ops[i] = Operator.values()[readMagnitude(in)];
        }
        MethodConstant method = (MethodConstant) res.getConstant(readMagnitude(in));

        this.exprs  = exprs;
        this.ops    = ops;
        this.method = method;
        booleanType = res.typeForName("Boolean");
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        prepareASTArray(exprs, res);
        res.register(method);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        int count = exprs.length;
        writePackedLong(out, count);
        for (ExprAST expr : exprs) {
            expr.writeExpr(out, res);
        }
        for (Operator op : ops) {
            writePackedLong(out, op.ordinal());
        }
        writePackedLong(out, res.indexOf(method));
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder();
        buf.append(exprs[0]);
        for (int i = 1, c = exprs.length; i < c; i++) {
            buf.append(' ')
               .append(ops[i-1].text)
               .append(' ')
               .append(exprs[i]);
        }
        return buf.toString();
    }
}