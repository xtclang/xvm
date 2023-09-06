package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import static org.xvm.asm.ast.BinaryAST.NodeType.BindFunctionExpr;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Bind function's arguments.
 */
public class BindFunctionAST<C>
        extends ExprAST<C>
    {
    private ExprAST<C>   target;
    private int[]        indexes;
    private ExprAST<C>[] args;
    private C            type;

    BindFunctionAST() {
    }

    /**
     * Construct an BindFunctionAST.
     *
     * @param type  the type of the resulting (bound) function
     */
    public BindFunctionAST(ExprAST<C> target, int[] indexes, ExprAST<C>[] args, C type) {
        assert target != null && indexes != null && type != null;
        assert args != null && args.length == indexes.length &&
                Arrays.stream(args).allMatch(Objects::nonNull);

        this.target  = target;
        this.indexes = indexes;
        this.args    = args;
        this.type    = type;
    }

    @Override
    public NodeType nodeType() {
        return BindFunctionExpr;
    }

    @Override
    public C getType(int i) {
        assert i == 0;
        return type;
    }

    public ExprAST<C> getTarget() {
        return target;
    }

    public int[] getIndexes() {
        return indexes;
    }

    public ExprAST<C>[] getArgs() {
        return args;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver<C> res)
            throws IOException {
        target = readExprAST(in, res);

        int count = readMagnitude(in);
        if (count == 0) {
            indexes = new int[0];
            args    = NO_EXPRS;
        } else {
            indexes = new int[count];
            args    = new ExprAST[count];
            for (int i = 0; i < count; ++i) {
                indexes[i] = readMagnitude(in);
                args[i]    = readExprAST(in, res);
            }
        }
        type = res.getConstant(readMagnitude(in));
    }

    @Override
    public void prepareWrite(ConstantResolver<C> res) {
        target.prepareWrite(res);
        prepareASTArray(args, res);
        type = res.register(type);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver<C> res)
            throws IOException {
        target.writeExpr(out, res);

        int count = indexes.length;
        writePackedLong(out, count);
        for (int i = 0; i < count; ++i) {
            writePackedLong(out, indexes[i]);
            args[i].writeExpr(out, res);
        }
        writePackedLong(out, res.indexOf(type));
    }

    @Override
    public String dump() {
        StringBuilder buf = new StringBuilder("&");
        buf.append(target.dump())
           .append("(");
        if (indexes.length > 0) {
            buf.append("\n");
            for (int i = 0, argIx = 0, max = Arrays.stream(indexes).max().getAsInt(); argIx < max; i++) {
                if (argIx != 0) {
                    buf.append(",\n");
                }
                if (i == indexes[argIx]) {
                    buf.append(args[i].dump());
                    argIx++;
                } else {
                    buf.append("_");
                }
            }
        }
        buf.append(")");
        return buf.toString();
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("&");
        buf.append(target)
           .append("(");
        if (indexes.length > 0) {
            for (int i = 0, argIx = 0, max = Arrays.stream(indexes).max().getAsInt(); argIx <= max; i++) {
                if (argIx != 0) {
                    buf.append(", ");
                }
                if (i == indexes[argIx]) {
                    buf.append(args[i]);
                    argIx++;
                } else {
                    buf.append("_");
                }
            }
        }
        buf.append(")");
        return buf.toString();
    }
}