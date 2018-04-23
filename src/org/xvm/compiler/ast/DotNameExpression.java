package org.xvm.compiler.ast;


import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Token;

import java.lang.reflect.Field;

import java.util.List;


/**
 * If you already have an expression "expr", this is for "expr.name".
 *
 * The DotName construct _may_ be a type expression.
 */
public class DotNameExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public DotNameExpression(Expression expr, Token suppressDereference, Token name, List<TypeExpression> params, long lEndPos)
        {
        this.expr    = expr;
        this.amp     = suppressDereference;
        this.name    = name;
        this.params  = params;
        this.lEndPos = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean isSuppressDeref()
        {
        return amp != null;
        }

    public String getName()
        {
        return name.getValue().toString();
        }

    public List<TypeExpression> getParams()
        {
        return params;
        }

    @Override
    protected TypeConstant instantiateTypeConstant()
        {
        // TODO
        throw new UnsupportedOperationException("build type for: " + this);
        }

    @Override
    public boolean validateCondition(ErrorListener errs)
        {
        return expr instanceof LiteralExpression
                && ((LiteralExpression) expr).literal.getId() == Token.Id.LIT_STRING
                && ((String) name.getValue()).equals("present")
                || super.validateCondition(errs);
        }

    @Override
    public ConditionalConstant toConditionalConstant()
        {
        return validateCondition(null)
                ? pool().ensureNamedCondition((String) ((LiteralExpression) expr).literal.getValue())
                : super.toConditionalConstant();
        }

    @Override
    public long getStartPosition()
        {
        return expr.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
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

        sb.append(expr)
          .append('.');

        if (amp != null)
            {
            sb.append('&');
            }

        sb.append(name.getValue());

        if (params != null)
            {
            sb.append('<');
            boolean first = true;
            for (TypeExpression type : params)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(type);
                }
            sb.append('>');
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression           expr;
    protected Token                amp;
    protected Token                name;
    protected List<TypeExpression> params;
    protected long                 lEndPos;

    private static final Field[] CHILD_FIELDS =
            fieldsForNames(DotNameExpression.class, "expr", "params");
    }
