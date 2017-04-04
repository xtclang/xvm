package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.util.List;


/**
 * A type expression specifies a named type with optional parameters.
 *
 * @author cp 2017.03.31
 */
public class NamedTypeExpression
        extends TypeExpression
    {
    public NamedTypeExpression(Token immutable, List<Token> names, List<TypeExpression> params)
        {
        this.immutable  = immutable;
        this.names      = names;
        this.paramTypes = params;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (immutable != null)
            {
            sb.append("immutable ");
            }

        boolean first = true;
        for (Token name : names)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append('.');
                }
            sb.append(name.getValue());
            }

        if (paramTypes != null)
            {
            sb.append('<');
            first = true;
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
            sb.append('>');
            }

        return sb.toString();
        }

    public final Token immutable;
    public final List<Token> names;
    public final List<TypeExpression> paramTypes;
    }
