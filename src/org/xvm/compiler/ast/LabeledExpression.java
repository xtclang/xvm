package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;


/**
 * Used for named arguments.
 */
public class LabeledExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public LabeledExpression(Token name, Expression expr)
        {
        this.name = name;
        this.expr = expr;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the token that provides the label (the name) for the expression
     */
    public Token getNameToken()
        {
        return name;
        }

    /**
     * @return the label name
     */
    public String getName()
        {
        return name.getValueText();
        }

    @Override
    public long getStartPosition()
        {
        return name.getStartPosition();
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


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public boolean isNonBinding()
        {
        return expr.isNonBinding();
        }

    @Override
    public boolean isConstant()
        {
        return expr.isConstant();
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return name + " = " + expr;
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token      name;
    protected Expression expr;

    private static final Field[] CHILD_FIELDS = fieldsForNames(LabeledExpression.class, "expr");
    }
