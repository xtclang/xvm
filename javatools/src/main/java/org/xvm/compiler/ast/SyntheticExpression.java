package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.ErrorListener;

import org.xvm.asm.ast.ConstantExprAST;
import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.UnaryOpExprAST;
import org.xvm.asm.ast.UnaryOpExprAST.Operator;

import org.xvm.compiler.Compiler.Stage;


/**
 * A synthetic expression is one created as necessary by the compilation process to add
 * common functionality to various nodes of the AST.
 */
public abstract class SyntheticExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public SyntheticExpression(Expression expr)
        {
        this.expr = expr;

        expr.getParent().adopt(this);
        this.adopt(expr);
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return get the TypeExpression for the tuple, if any; otherwise return null
     */
    public Expression getUnderlyingExpression()
        {
        return expr;
        }

    @Override
    public Stage getStage()
        {
        Stage stageThis = super.getStage();
        Stage stageThat = expr.getStage();
        return stageThis.compareTo(stageThat) > 0
                ? stageThis
                : stageThat;
        }

    @Override
    public long getStartPosition()
        {
        return expr.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return expr.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- Expression compilation ----------------------------------------------------------------

    @Override
    public boolean isAssignable(Context ctx)
        {
        return expr.isAssignable(ctx);
        }

    @Override
    public void requireAssignable(Context ctx, ErrorListener errs)
        {
        expr.requireAssignable(ctx, errs);
        }

    @Override
    public void markAssignment(Context ctx, boolean fCond, ErrorListener errs)
        {
        expr.markAssignment(ctx, fCond, errs);
        }

    @Override
    public boolean isCompletable()
        {
        return expr.isCompletable();
        }

    @Override
    public boolean isShortCircuiting()
        {
        return expr.isShortCircuiting();
        }

    @Override
    public ExprAST getExprAST(Context ctx)
        {
        if (isConstant())
            {
            return new ConstantExprAST(toConstant());
            }

        Operator op;
        if (this instanceof PackExpression)
            {
            op = Operator.Pack;
            }
        else if (this instanceof ToIntExpression)
            {
            op = Operator.ToInt;
            }
        else if (this instanceof TraceExpression)
            {
            op = Operator.Trace;
            }
        else
            {
            throw new UnsupportedOperationException();
            }

        return new UnaryOpExprAST(expr.getExprAST(ctx), op, getType());
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public abstract String toString();

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    /**
     * The modified expression.
     */
    protected Expression expr;

    private static final Field[] CHILD_FIELDS = fieldsForNames(SyntheticExpression.class, "expr");
    }