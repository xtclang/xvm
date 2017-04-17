package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import org.xvm.util.Handy;
import org.xvm.util.ListMap;

import java.util.List;
import java.util.Map;


/**
 * "New object" expression.
 *
 * @author cp 2017.04.06
 */
public class NewExpression
        extends PrefixExpression
    {
    // ----- constructors --------------------------------------------------------------------------

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


    // ----- accessors -----------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

    public String toSignatureString()
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

        if (args != null)
            {
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
            }

        sb.append(')');

        return sb.toString();
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(toSignatureString());

        if (body != null)
            {
            sb.append('\n')
              .append(Handy.indentLines(body.toString(), "        "));
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toSignatureString();
        }

    @Override
    public Map<String, Object> getDumpChildren()
        {
        ListMap<String, Object> map = new ListMap();
        map.put("parent", parent);
        map.put("args", args);
        map.put("body", body);
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression       parent;
    protected List<Expression> args;
    protected StatementBlock   body;
    }
