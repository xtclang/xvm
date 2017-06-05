package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;


/**
 * A throw statement throws an exception.
 *
 * @author cp 2017.04.09
 */
public class ThrowStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public ThrowStatement(Token keyword, Expression expr)
        {
        this.keyword = keyword;
        this.expr    = expr;
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
        return expr.getEndPosition();
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
        return "throw " + expr.toString() + ';';
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token      keyword;
    protected Expression expr;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ThrowStatement.class, "expr");
    }
