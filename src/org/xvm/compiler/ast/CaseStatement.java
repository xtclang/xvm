package org.xvm.compiler.ast;


import java.util.List;

import org.xvm.compiler.Token;

import java.lang.reflect.Field;


/**
 * A case statement. This can only occur within a switch statement. (It's not a "real" statement;
 * it's more like a label.)
 */
public class CaseStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public CaseStatement(Token keyword, List<Expression> exprs, Token tokColon)
        {
        this.keyword = keyword;
        this.exprs   = exprs;
        this.lEndPos = tokColon.getEndPosition();
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
        return lEndPos;
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

        if (exprs != null)
            {
            sb.append(' ')
              .append(exprs.get(0));

            for (int i = 1, c = exprs.size(); i < c; ++i)
                {
                sb.append(", ")
                  .append(exprs.get(i));
                }
            }

        sb.append(':');

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token            keyword;
    protected List<Expression> exprs;
    protected long             lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(CaseStatement.class, "expr");
    }
