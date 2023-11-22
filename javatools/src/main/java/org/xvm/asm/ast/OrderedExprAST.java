package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.constants.TypeConstant;


/**
 * An expressions that produces a Boolean based on Ecstasy "Ordered" enum value and a specified
 * comparison operation.
 *
 * For example, an expression "a < b" will produce an BAST of
 *      Ordered(Call("compare", [a, b]), Less)
 * and an expression "a <= b" will produce
 *      Not(Ordered(Call("compare", [a, b]), Greater))
 */
public class OrderedExprAST
        extends DelegatingExprAST {

    private final NodeType nodeType;
    private final Operator op;
    private transient TypeConstant booleanType;  // TODO CP remove

    public enum Operator {
        Less   (NodeType.Less),
        Greater(NodeType.Greater),
        ;

        public final NodeType nodeType;

        Operator(NodeType nodeType) {
            this.nodeType = nodeType;
        }
    }

    OrderedExprAST(NodeType nodeType) {
        this.op = switch (nodeType) {
            case Less    -> Operator.Less;
            case Greater -> Operator.Greater;
            default      -> throw new IllegalArgumentException("nodeType=" + nodeType);
        };
        this.nodeType = nodeType;
    }

    public OrderedExprAST(ExprAST expr, Operator op) {
        super(expr);

        this.op = op;
        this.nodeType = switch (op) {
            case Less    -> NodeType.Less;
            case Greater -> NodeType.Greater;
        };
    }

    public Operator getOp() {
        return op;
    }

    @Override
    public TypeConstant getType(int i) {
        return booleanType;
    }

    @Override
    public NodeType nodeType() {
        return nodeType;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        super.readBody(in, res);

        booleanType = res.typeForName("Boolean");
    }


    @Override
    public String toString() {
        return getExpr() + " == " + op.toString();
    }
}