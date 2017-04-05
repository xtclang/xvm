package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.util.List;


/**
 * A type expression specifies a named type with optional parameters.
 *
 * @author cp 2017.03.31
 */
public class FunctionTypeExpression
        extends TypeExpression
    {
    public FunctionTypeExpression(Token function, List<TypeExpression> returnValues, List<TypeExpression> params)
        {
        this.function     = function;
        this.returnValues = returnValues;
        this.paramTypes   = params;
        }

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

    public final Token function;
    public final List<TypeExpression> returnValues;
    public final List<TypeExpression> paramTypes;
    }
