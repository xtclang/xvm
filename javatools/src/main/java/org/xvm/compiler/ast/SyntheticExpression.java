package org.xvm.compiler.ast;


import java.util.Objects;
import java.util.function.Function;

import org.jetbrains.annotations.NotNull;

import org.xvm.asm.Argument;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.UnaryOpExprAST;
import org.xvm.asm.ast.UnaryOpExprAST.Operator;

import org.xvm.compiler.Compiler.Stage;


/**
 * A synthetic expression is one created as necessary by the compilation process to add
 * common functionality to various nodes of the AST.
 */
public abstract class SyntheticExpression
        extends Expression {
    // ----- constructors --------------------------------------------------------------------------

    public SyntheticExpression(@NotNull Expression expr) {
        this.expr = Objects.requireNonNull(expr);

        expr.getParent().adopt(this);
        this.adopt(expr);
    }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return get the TypeExpression for the tuple, if any; otherwise return null
     */
    public Expression getUnderlyingExpression() {
        return expr;
    }

    @Override
    public Stage getStage() {
        Stage stageThis = super.getStage();
        Stage stageThat = expr.getStage();
        return stageThis.compareTo(stageThat) > 0
                ? stageThis
                : stageThat;
    }

    @Override
    public long getStartPosition() {
        return expr.getStartPosition();
    }

    @Override
    public long getEndPosition() {
        return expr.getEndPosition();
    }

    @Override
    public <T> T forEachChild(Function<AstNode, T> visitor) {
        return visitor.apply(expr);
    }

    @Override
    protected <T extends AstNode> void replaceChild(T oldChild, T newChild) {
        assertReplaced(tryReplace(oldChild, newChild, expr, n -> expr = n), oldChild);
    }


    // ----- Expression compilation ----------------------------------------------------------------

    @Override
    public boolean isAssignable(Context ctx) {
        return expr.isAssignable(ctx);
    }

    @Override
    public void requireAssignable(Context ctx, ErrorListener errs) {
        expr.requireAssignable(ctx, errs);
    }

    @Override
    public void markAssignment(Context ctx, boolean fCond, ErrorListener errs) {
        expr.markAssignment(ctx, fCond, errs);
    }

    @Override
    public boolean isCompletable() {
        return expr.isCompletable();
    }

    @Override
    public boolean isShortCircuiting() {
        return expr.isShortCircuiting();
    }

    @Override
    public ExprAST getExprAST(Context ctx) {
        if (isConstant()) {
            return expr.getExprAST(ctx);
        }

        Operator op = switch (this) {
            case PackExpression  ignored -> Operator.Pack;
            case ToIntExpression ignored -> Operator.ToInt;
            case TraceExpression ignored -> Operator.Trace;
            default                      -> throw new UnsupportedOperationException();
        };

        return new UnaryOpExprAST(expr.getExprAST(ctx), op, getType());
    }

    @Override
    protected SideEffect mightAffect(Expression exprLeft, Argument arg) {
        return expr.mightAffect(exprLeft, arg);
    }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public abstract String toString();

    @Override
    public String getDumpDesc() {
        return toString();
    }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The modified expression.
     */
    protected @NotNull Expression expr;
}