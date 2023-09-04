package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.ast.BinaryAST.ExprAST;
import org.xvm.asm.ast.ConstantExprAST;
import org.xvm.asm.ast.ConvertExprAST;
import org.xvm.asm.ast.SyntheticExprAST;
import org.xvm.asm.ast.SyntheticExprAST.Operation;

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
    public ExprAST<Constant> getExprAST()
        {
        if (isConstant())
            {
            return new ConstantExprAST<>(getType(), toConstant());
            }

        if (this instanceof ConvertExpression exprConv)
            {
            return new ConvertExprAST<>(getType(), expr.getExprAST(), exprConv.getConversionMethod());
            }

        Operation op;
        if (this instanceof PackExpression)
            {
            op = Operation.Pack;
            }
        else if (this instanceof UnpackExpression)
            {
            op = Operation.Unpack;
            }
        else if (this instanceof ToIntExpression)
            {
            op = Operation.ToInt;
            }
        else if (this instanceof TraceExpression)
            {
            op = Operation.Trace;
            }
        else
            {
            throw new UnsupportedOperationException();
            }

        return new SyntheticExprAST<>(op, getType(), expr.getExprAST());
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