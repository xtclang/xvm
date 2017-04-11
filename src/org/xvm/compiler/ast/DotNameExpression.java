package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.util.List;


/**
 * If you already have an expression "expr", this is for "expr.name".
 *
 * @author cp 2017.04.08
 */
public class DotNameExpression
        extends Expression
    {
    public DotNameExpression(Expression expr, Token name, List<TypeExpression> params)
        {
        this.expr   = expr;
        this.name   = name;
        this.params = params;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(expr)
          .append('.')
          .append(name.getValue());

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

    public final Expression expr;
    public final Token      name;
    public final List<TypeExpression> params;
    }
