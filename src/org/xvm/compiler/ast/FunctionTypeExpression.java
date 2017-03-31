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
    public FunctionTypeExpression(Token function, Token name, List<TypeExpression> returnValues, List<Parameter> params)
        {
        this.function     = function;
        this.name         = name;
        this.returnValues = returnValues;
        this.params       = params;
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
        for (Parameter param : params)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(param.toTypeParamString());
            }

        sb.append(") ")
          .append(name.getValue());

        return sb.toString();
        }

    public final Token function;
    public final Token name;
    public final List<TypeExpression> returnValues;
    public final List<Parameter> params;
    }
