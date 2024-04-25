package org.xvm.asm.ast;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.TypeConstant;

import static org.xvm.util.Handy.readMagnitude;
import static org.xvm.util.Handy.writePackedLong;


/**
 * An expressions that follows the pattern "operator expression" or "expression operator" and may
 * change the type of the underlying expression.
 */
public class UnaryOpExprAST
        extends UnaryExprAST {
    private Operator op;

    public enum Operator {
        Not       ("!"       , true , NodeType.NotExpr    ),
        Minus     ("-"       , true , NodeType.NegExpr    ),
        Compl     ("~"       , true , NodeType.BitNotExpr ),
        PreInc    ("++"      , true , NodeType.PreIncExpr ),
        PreDec    ("--"      , true , NodeType.PreDecExpr ),
        PostInc   ("++"      , false, NodeType.PostIncExpr),
        PostDec   ("--"      , false, NodeType.PostDecExpr),
        Ref       ("&"       , true , NodeType.RefOfExpr  ),
        Var       ("&"       , true , NodeType.VarOfExpr  ),
        Type      ("typeOf:" , true , NodeType.UnaryOpExpr),
        Private   ("private:", true , NodeType.UnaryOpExpr),
        Protected ("private:", true , NodeType.UnaryOpExpr),
        Public    ("public:" , true , NodeType.UnaryOpExpr),
        Pack      (""        , true , NodeType.UnaryOpExpr),
        ToInt     (".TOINT()", false, NodeType.UnaryOpExpr),
        Trace     (".TRACE()", false, NodeType.UnaryOpExpr),
        ;

        public final String   text;
        public final boolean  pre;
        public final NodeType nodeType;

        Operator(String text, boolean pre, NodeType nodeType) {
            this.text     = text;
            this.pre      = pre;
            this.nodeType = nodeType;
        }
    }

    UnaryOpExprAST() {}

    UnaryOpExprAST(NodeType nodeType) {
        op = switch (nodeType) {
            case NotExpr      -> Operator.Not;
            case NegExpr      -> Operator.Minus;
            case BitNotExpr   -> Operator.Compl;
            case PreIncExpr   -> Operator.PreInc;
            case PreDecExpr   -> Operator.PreDec;
            case PostIncExpr  -> Operator.PostInc;
            case PostDecExpr  -> Operator.PostDec;
            case RefOfExpr    -> Operator.Ref;
            case VarOfExpr    -> Operator.Var;
            default           -> throw new IllegalArgumentException("nodeType=" + nodeType);
        };
    }

    public UnaryOpExprAST(ExprAST expr, Operator op, TypeConstant type) {
        super(expr, type);
        this.op = op;
    }

    public Operator getOp() {
        return op;
    }

    @Override
    public int getCount() {
        // "Trace" is a "pass-through" operation; infer the answer from the underlying expression
        return op == Operator.Trace
                ? getExpr().getCount()
                : super.getCount();
    }

    @Override
    public TypeConstant getType(int i) {
        // ditto
        return op == Operator.Trace
                ? getExpr().getType(i)
                : super.getType(i);
    }

    @Override
    public boolean isConditional() {
        // ditto
        return op == Operator.Trace
                ? getExpr().isConditional()
                : super.isConditional();
    }

    @Override
    public NodeType nodeType() {
        return op == null ? NodeType.UnaryOpExpr : op.nodeType;
    }

    @Override
    protected void readBody(DataInput in, ConstantResolver res)
            throws IOException {
        super.readBody(in, res);

        if (op == null) {
            op = Operator.values()[readMagnitude(in)];
        }
    }

    @Override
    protected void writeBody(DataOutput out, ConstantResolver res)
            throws IOException {
        super.writeBody(out, res);

        if (op.nodeType == NodeType.UnaryOpExpr) {
            writePackedLong(out, op.ordinal());
        }
    }

    @Override
    public String toString() {
        String expr = getExpr().toString();
        if (expr.contains(" ")) {
            expr = '(' + expr + ')';
        }
        return op.pre ? op.text + expr : expr + op.text;
    }
}