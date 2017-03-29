package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.util.List;


/**
 * A type expression specifies a type.
 * TODO - this is a place-holder for now
 *
 * @author cp 2017.03.28
 */
public class TypeExpression
        extends Expression
    {
    public TypeExpression(Token immutable, List<Token> names, List<Parameter> params)
        {
        this.immutable = immutable;
        this.names     = names;
        this.params    = params;
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

        if (params != null)
            {
            sb.append('<');
            first = true;
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
            sb.append('>');
            }

        return sb.toString();
        }

    public final Token immutable;
    public final List<Token> names;
    public final List<Parameter> params;
    }
