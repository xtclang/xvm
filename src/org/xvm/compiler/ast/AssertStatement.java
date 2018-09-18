package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;


/**
 * An assert statement.
 */
public class AssertStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public AssertStatement(Token keyword, AstNode cond)
        {
        this.keyword = keyword;
        this.cond = cond;
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
        return cond == null ? keyword.getEndPosition() : cond.getEndPosition();
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

        if (cond != null)
            {
            sb.append(' ')
              .append(cond);
            }

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token   keyword;
    protected AstNode cond;

    private static final Field[] CHILD_FIELDS = fieldsForNames(AssertStatement.class, "cond");
    }
