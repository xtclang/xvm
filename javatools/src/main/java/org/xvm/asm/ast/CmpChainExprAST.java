package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.jetbrains.annotations.NotNull;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.ast.BiExprAST.Operator;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * Comparison over a chain of expressions.
 */
// TODO: make fields final once AST deserialization supports factory-based construction
public class CmpChainExprAST
        extends ExprAST {

    private List<ExprAST>  exprs;
    private List<Operator> ops;
    private MethodConstant method;

    private transient TypeConstant booleanType;

    CmpChainExprAST() {}

    public CmpChainExprAST(@NotNull List<ExprAST> exprs, @NotNull List<Operator> ops, @NotNull MethodConstant method) {
        Objects.requireNonNull(exprs);
        Objects.requireNonNull(ops);
        Objects.requireNonNull(method);

        assert exprs.stream().allMatch(Objects::nonNull);
        assert ops.stream().allMatch(Objects::nonNull);
        assert ops.size() == exprs.size() - 1;

        assert ops.stream().allMatch(op -> switch (op) {
            case CompEq, CompNeq, CompLt, CompGt, CompLtEq, CompGtEq -> true;
            default -> false;
        });
        this.exprs  = List.copyOf(exprs);
        this.ops    = List.copyOf(ops);
        this.method = method;
    }

    public List<Operator> getOps() {
        return ops;
    }

    public List<ExprAST> getExprs() {
        return exprs;
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

        var exprs = new ArrayList<ExprAST>(count);
        for (int i = 0; i < count; ++i) {
            exprs.add(readExprAST(in, res));
        }
        var ops = new ArrayList<Operator>(count - 1);
        for (int i = 0; i < count - 1; ++i) {
            ops.add(Operator.values()[readMagnitude(in)]);
        }
        MethodConstant method = res.getConstant(readMagnitude(in), MethodConstant.class);

        this.exprs  = List.copyOf(exprs);
        this.ops    = List.copyOf(ops);
        this.method = method;
        booleanType = res.typeForName("Boolean");
    }

    @Override
    public void prepareWrite(ConstantResolver res) {
        exprs.forEach(expr -> expr.prepareWrite(res));
        res.register(method);
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        writePackedLong(out, exprs.size());
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
        return exprs.getFirst() + IntStream.range(1, exprs.size())
                .mapToObj(i -> " " + ops.get(i - 1).text + " " + exprs.get(i))
                .collect(Collectors.joining());
    }
}
