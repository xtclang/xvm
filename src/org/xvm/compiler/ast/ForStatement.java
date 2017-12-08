package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;

import java.util.List;

import static org.xvm.util.Handy.indentLines;


/**
 * The traditional "for" statement.
 */
public class ForStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public ForStatement(Token keyword, List<Statement> init, Expression expr,
                        List<Statement> update, StatementBlock block)
        {
        this.keyword = keyword;
        this.init    = init;
        this.expr    = expr;
        this.update  = update;
        this.block   = block;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return block.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("for (");

        if (init != null)
            {
            boolean first = true;
            for (Statement stmt : init)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(stmt);
                }
            }

        sb.append("; ");

        if (expr != null)
            {
            sb.append(expr);
            }

        sb.append("; ");

        if (update != null)
            {
            boolean first = true;
            for (Statement stmt : update)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(stmt);
                }
            }

        sb.append(")\n")
          .append(indentLines(block.toString(), "    "));

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token           keyword;
    protected List<Statement> init;
    protected Expression      expr;
    protected List<Statement> update;
    protected StatementBlock  block;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ForStatement.class, "init", "expr", "update", "block");
    }
