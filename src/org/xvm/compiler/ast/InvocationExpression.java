package org.xvm.compiler.ast;


import java.util.List;


/**
 * Invocation expression represents calling a method or function.
 *
 * If you already have an expression "expr", this is for "expr(args)".
 *
 * @author cp 2017.04.08
 */
public class InvocationExpression
        extends Expression
    {
    public InvocationExpression(Expression expr, List<Expression> args)
        {
        this.expr = expr;
        this.args = args;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        sb.append(expr)
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

    public final Expression       expr;
    public final List<Expression> args;
    }
