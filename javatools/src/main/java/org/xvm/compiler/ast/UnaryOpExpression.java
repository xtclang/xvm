package org.xvm.compiler.ast;


import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.UnaryOpExprAST;
import org.xvm.asm.ast.UnaryOpExprAST.Operator;


/**
 * A synthetic expression that compiles down to a {@link UnaryOpExprAST} over its underlying
 * expression, and is distinguished from its siblings only by which {@link Operator} it carries.
 * <p/>
 * This tier exists because {@link SyntheticExpression} has two kinds of subclass, and only one of
 * them is a unary operation. {@link ConvertExpression} and {@link UnpackExpression} build entirely
 * different AST nodes and override {@code getExprAST} to do so; the three permitted here do not,
 * and share an implementation that varies by a single constant.
 */
public abstract sealed class UnaryOpExpression
        extends SyntheticExpression
        permits PackExpression, ToIntExpression, TraceExpression {
    // ----- constructors --------------------------------------------------------------------------

    protected UnaryOpExpression(Expression expr, Operator op) {
        super(expr);

        f_op = op;
    }


    // ----- Expression compilation ----------------------------------------------------------------

    @Override
    public ExprAST getExprAST(Context ctx) {
        if (isConstant()) {
            return expr.getExprAST(ctx);
        }

        return new UnaryOpExprAST(expr.getExprAST(ctx), f_op, getType());
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The operator this synthetic expression represents.
     */
    private final Operator f_op;


    // ----- copy support --------------------------------------------------------------------------

    /**
     * Shallow copy constructor for {@link AstNode#deepCopy()}: every declared field of this
     * tier is carried over verbatim (children are re-copied and re-adopted by the walk); the
     * field parity assertion in deepCopy() fails loudly if a field is added but not copied.
     */
    protected UnaryOpExpression(UnaryOpExpression that) {
        super(that);

        f_op = that.f_op;
    }
}
