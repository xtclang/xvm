package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.util.List;


/**
 * Lambda expression is an inlined function. This version uses parameters that are assumed to be
 * names only.
 *
 * @author cp 2017.04.07
 */
public class ImplicitLambdaExpression
        extends Expression
    {
    public ImplicitLambdaExpression(List<Expression> params, Token operator, StatementBlock body)
        {
        this.params   = params;
        this.operator = operator;
        this.body     = body;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append('(');
        boolean first = true;
        for (Expression param : params)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(param);
            }

        sb.append(')')
          .append(' ')
          .append(operator.getId().TEXT)
          .append(' ')
          .append(body);

        return sb.toString();
        }

    public final List<Expression> params;
    public final Token            operator;
    public final StatementBlock body;
    }
