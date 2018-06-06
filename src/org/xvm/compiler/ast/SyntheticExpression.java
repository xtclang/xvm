package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler.Stage;

import org.xvm.compiler.ast.Statement.Context;


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

        expr.getParent().introduceParentage(this);
        this.introduceParentage(expr);
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
    public boolean isAssignable()
        {
        return expr.isAssignable();
        }

    @Override
    public boolean isAborting()
        {
        return expr.isAborting();
        }

    @Override
    public boolean isShortCircuiting()
        {
        return expr.isShortCircuiting();
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
