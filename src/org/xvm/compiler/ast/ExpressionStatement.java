package org.xvm.compiler.ast;


import java.lang.reflect.Field;


/**
 * An expression statement is just an expression that someone stuck a semicolon on the end of.
 *
 * @author cp 2017.04.03
 */
public class ExpressionStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public ExpressionStatement(Expression expr)
        {
        this(expr, true);
        }

    public ExpressionStatement(Expression expr, boolean standalone)
        {
        this.expr = expr;
        this.term = standalone;
        }


    // ----- accessors -----------------------------------------------------------------------------

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
        sb.append(expr);
        if (term)
            {
            sb.append(';');
            }
        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        String s  = toString();
        int    of = s.indexOf('\n');
        return (of < 0) ? s : s.substring(0, of) + " ...";
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression expr;
    protected boolean    term;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ExpressionStatement.class, "expr");
    }
