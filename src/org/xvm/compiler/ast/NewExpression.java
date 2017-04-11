package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.util.List;


/**
 * "New object" expression.
 *
 * @author cp 2017.04.06
 */
public class NewExpression
        extends PrefixExpression
    {
    /**
     * Prefix "new"
     *
     * @param operator
     * @param expr
     * @param args
     */
    public NewExpression(Token operator, Expression expr, List<Expression> args, StatementBlock body)
        {
        super(operator, expr);
        this.parent = null;
        this.args   = args;
        this.body   = body;
        }

    /**
     * Postfix ".new"
     *
     * @param parent
     * @param operator
     * @param expr
     * @param args
     */
    public NewExpression(Expression parent, Token operator, Expression expr, List<Expression> args)
        {
        super(operator, expr);
        this.parent = parent;
        this.args   = args;
        this.body   = null;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (parent != null)
            {
            sb.append(parent)
              .append('.');
            }

        sb.append(operator.getId().TEXT)
          .append(' ')
          .append(expr)
          .append('(');

        boolean first = true;
        for (Expression arg : args)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }
            sb.append(arg);
            }
        sb.append(')');

        return sb.toString();
        }

    public final Expression       parent;
    public final List<Expression> args;
    public final StatementBlock   body;
    }
