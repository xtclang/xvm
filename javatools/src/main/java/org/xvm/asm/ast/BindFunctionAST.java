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
import org.xvm.util.FrozenIntArray;


/**
 * Bind function's arguments.
 */
public final class BindFunctionAST
        extends ExprAST {
    private ExprAST      target;
    private FrozenIntArray indexes;
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
        this.indexes = FrozenIntArray.copyOf(indexes);
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

    /**
     * @return the bound-argument indexes; frozen, because this handed out a mutable alias of the
     *         AST node's own storage
     */
    public FrozenIntArray getIndexes() {
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
            indexes = FrozenIntArray.EMPTY;
            args    = NO_EXPRS;
        } else {
            // build into a local, then adopt: the frozen wrapper is published complete rather
            // than filled in place after construction
            int[] aiIndex = new int[count];
            args = new ExprAST[count];
            for (int i = 0; i < count; ++i) {
                aiIndex[i] = readMagnitude(in);
                args[i]    = readExprAST(in, res);
            }
            indexes = FrozenIntArray.adopt(aiIndex);
        }
        type = res.getConstant(readMagnitude(in), TypeConstant.class);
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

        int count = indexes.size();
        writePackedLong(out, count);
        for (int i = 0; i < count; ++i) {
            writePackedLong(out, indexes.get(i));
            args[i].writeExpr(out, res);
        }
        writePackedLong(out, res.indexOf(type));
    }

    @Override
    public String toString() {
        StringBuilder buf = new StringBuilder("&");
        buf.append(target)
           .append("(");
        if (!indexes.isEmpty()) {
            for (int i = 0, argIx = 0, max = indexes.stream().max().getAsInt(); argIx <= max; i++) {
                if (argIx != 0) {
                    buf.append(", ");
                }
                if (i == indexes.get(argIx)) {
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
