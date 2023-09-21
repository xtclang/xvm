package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.Arrays;
import java.util.Objects;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.asm.ast.BinaryAST.NodeType.BindFunctionExpr;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Bind function's arguments.
 */
public class BindFunctionAST
        extends ExprAST
    {
    private ExprAST      target;
    private int[]        indexes;
    private ExprAST[]    args;
    private TypeConstant type;

    BindFunctionAST() {
    }

    /**
     * Construct an BindFunctionAST.
     *
     * @param type  the type of the resulting (bound) function
     */
    public BindFunctionAST(ExprAST target, int[] indexes, ExprAST[] args, TypeConstant type) {
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
    public TypeConstant getType(int i) {
        assert i == 0;
        return type;
    }

    public ExprAST getTarget() {
        return target;
    }

    public int[] getIndexes() {
        return indexes;
    }

    public ExprAST[] getArgs() {
        return args;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
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
        type = (TypeConstant) res.getConstant(readMagnitude(in));
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        target.prepareWrite(res);
        prepareASTArray(args, res);
        type = (TypeConstant) res.register(type);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
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