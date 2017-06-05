package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;


/**
 * Used for named arguments.
 *
 * @author cp 2017.04.08
 */
public class NamedExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public NamedExpression(Token name, Expression expr)
        {
        this.name = name;
        this.expr = expr;
        }


    // ----- accessors -----------------------------------------------------------------------------

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

    protected Expression expr;
    protected Token      name;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NamedExpression.class, "expr");
    }
