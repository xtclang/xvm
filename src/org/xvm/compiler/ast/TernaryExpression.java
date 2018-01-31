package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Constant;


/**
 * A ternary expression is the "a ? b : c" expression.
 */
public class TernaryExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public TernaryExpression(Expression cond, Expression exprThen, Expression exprElse)
        {
        this.cond     = cond;
        this.exprThen = exprThen;
        this.exprElse = exprElse;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return cond.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return exprElse.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public boolean isConstant()
        {
        if (!cond.isConstant())
            {
            return false;
            }

        Constant constant = cond.toConstant();
        if (constant == pool().valTrue())
            {
            return exprThen.isConstant();
            }
        else if (constant == pool().valFalse())
            {
            return exprElse.isConstant();
            }
        else
            {
            return false;
            }
        }

    @Override
    public Constant toConstant()
        {
        Constant constant = cond.toConstant();
        if (constant == pool().valTrue())
            {
            return exprThen.toConstant();
            }
        else if (constant == pool().valFalse())
            {
            return exprElse.toConstant();
            }
        else
            {
            return super.toConstant();
            }
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(cond)
          .append(" ? ")
          .append(exprThen)
          .append(" : ")
          .append(exprElse);

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression cond;
    protected Expression exprThen;
    protected Expression exprElse;

    private static final Field[] CHILD_FIELDS = fieldsForNames(TernaryExpression.class, "cond", "exprThen", "exprElse");
    }
