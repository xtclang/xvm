package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;


/**
 * A case statement. This can only occur within a switch statement. (It's not a "real" statement;
 * it's more like a label.)
 *
 * @author cp 2017.04.09
 */
public class CaseStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public CaseStatement(Token keyword, Expression expr, Token tokColon)
        {
        this.keyword = keyword;
        this.expr    = expr;
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

        if (expr != null)
            {
            sb.append(' ')
              .append(expr);
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

    protected Token      keyword;
    protected Expression expr;
    protected long       lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(CaseStatement.class, "expr");
    }
