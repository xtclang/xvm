package org.xvm.compiler.ast;


import org.xvm.compiler.Token;


/**
 * A type parameter with an optional type type.
 *
 * @author cp 2017.03.28
 */
public class Parameter
    {
    public Parameter(TypeExpression type, Token name)
        {
        this (type, name, null);
        }

    public Parameter(TypeExpression type, Token name, Expression value)
        {
        this.type  = type;
        this.name  = name;
        this.value = value;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(type)
          .append(' ')
          .append(name.getValue());

        if (value != null)
            {
            sb.append(" = ")
              .append(value);
            }

        return sb.toString();
        }

    public String toTypeParamString()
        {
        String s = String.valueOf(name.getValue());
        return type == null ? s : (s + " extends " + type);
        }

    public final TypeExpression type;
    public final Token name;
    public final Expression value;
    }
