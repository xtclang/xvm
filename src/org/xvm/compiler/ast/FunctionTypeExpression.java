package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;

import java.util.List;


/**
 * A type expression for a function. This corresponds to the "function" keyword.
 *
 * @author cp 2017.03.31
 */
public class FunctionTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public FunctionTypeExpression(Token function, List<TypeExpression> returnValues,
            List<TypeExpression> params, long lEndPos)
        {
        this.function     = function;
        this.returnValues = returnValues;
        this.paramTypes   = params;
        this.lEndPos      = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------


    @Override
    public long getStartPosition()
        {
        return function.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return  lEndPos;
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

        sb.append("function ");

        if (returnValues.isEmpty())
            {
            sb.append("Void");
            }
        else if (returnValues.size() == 1)
            {
            sb.append(returnValues.get(0));
            }
        else
            {
            boolean first = true;
            for (TypeExpression type : returnValues)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append('.');
                    }
                sb.append(type);
                }
            }

        sb.append(" (");

        boolean first = true;
        for (TypeExpression type : paramTypes)
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

        sb.append(')');

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token                function;
    protected List<TypeExpression> returnValues;
    protected List<TypeExpression> paramTypes;
    protected long                 lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(FunctionTypeExpression.class, "returnValues", "paramTypes");
    }
