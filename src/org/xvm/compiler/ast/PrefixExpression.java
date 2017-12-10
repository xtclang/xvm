package org.xvm.compiler.ast;


import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.ConditionalConstant;

import org.xvm.compiler.Token;

import java.lang.reflect.Field;


/**
 * Generic expression for something that follows the pattern "operator expression".
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
    public boolean validateCondition(ErrorListener errs)
        {
        return operator.getId() == Token.Id.NOT
                ? expr.validateCondition(errs)
                : super.validateCondition(errs);
        }

    @Override
    public ConditionalConstant toConditionalConstant()
        {
        return operator.getId() == Token.Id.NOT
                ? expr.toConditionalConstant().negate()
                : super.toConditionalConstant();
        }

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
