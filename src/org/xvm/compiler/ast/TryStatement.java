package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;

import java.util.Collections;
import java.util.List;

import static org.xvm.util.Handy.indentLines;


/**
 * A "try" or "using" statement.
 *
 * @author cp 2017.04.10
 */
public class TryStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public TryStatement(Token keyword, List<Statement> resources, StatementBlock block, List<CatchStatement> catches, StatementBlock catchall)
        {
        this.keyword   = keyword;
        this.resources = resources == null ? Collections.EMPTY_LIST : resources;
        this.block     = block;
        this.catches   = catches == null ? Collections.EMPTY_LIST : catches;
        this.catchall  = catchall;
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
        return catchall == null
                ? catches.isEmpty()
                        ? block.getEndPosition()
                        : catches.get(catches.size()-1).getEndPosition()
                : catchall.getEndPosition();
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

        sb.append(keyword.getId().TEXT);

        if (!resources.isEmpty())
            {
            sb.append(" (");
            boolean first = true;
            for (Statement resource : resources)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(resource);
                }
            sb.append(')');
            }

        sb.append('\n')
          .append(indentLines(block.toString(), "    "));

        for (CatchStatement catchone : catches)
            {
            sb.append('\n')
              .append(catchone);
            }

        if (catchall != null)
            {
            sb.append("\nfinally\n")
              .append(indentLines(catchall.toString(), "    "));
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return keyword.getId().TEXT;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token                keyword;
    protected List<Statement>      resources;
    protected StatementBlock       block;
    protected List<CatchStatement> catches;
    protected StatementBlock       catchall;

    private static final Field[] CHILD_FIELDS = fieldsForNames(TryStatement.class,
            "resources", "block", "catches", "catchall");
    }
