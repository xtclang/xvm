package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;


/**
 * Generic expression for something that follows the pattern "operator expression".
 *
 * @author cp 2017.04.06
 */
public class PrefixExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public PrefixExpression(Token operator, Expression expr)
        {
        this.operator = operator;
        this.expr     = expr;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return operator.getStartPosition();
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
        StringBuilder sb = new StringBuilder();

        sb.append(operator.getId().TEXT)
          .append(expr);

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token      operator;
    protected Expression expr;

    private static final Field[] CHILD_FIELDS = fieldsForNames(PrefixExpression.class, "expr");
    }
