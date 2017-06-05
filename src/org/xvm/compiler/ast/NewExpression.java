package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;

import java.util.List;

import static org.xvm.util.Handy.indentLines;


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
    public NewExpression(Token operator, Expression expr, List<Expression> args, StatementBlock body, long lEndPos)
        {
        super(operator, expr);
        this.cont    = null;
        this.args    = args;
        this.body    = body;
        this.lEndPos = lEndPos;
        }

    /**
     * Postfix ".new"
     *
     * @param cont
     * @param operator
     * @param expr
     * @param args
     */
    public NewExpression(Expression cont, Token operator, Expression expr, List<Expression> args, long lEndPos)
        {
        super(operator, expr);
        this.cont    = cont;
        this.args    = args;
        this.body    = null;
        this.lEndPos = lEndPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return cont == null ? operator.getStartPosition() : cont.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return lEndPos;
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    public String toSignatureString()
        {
        StringBuilder sb = new StringBuilder();

        if (cont != null)
            {
            sb.append(cont)
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
              .append(indentLines(body.toString(), "        "));
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toSignatureString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression       cont;
    protected List<Expression> args;
    protected StatementBlock   body;
    protected long             lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NewExpression.class, "cont", "args", "body");
    }
