package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;


/**
 * A labeled statement represents a statement that has a label.
 *
 * @author cp 2017.04.09
 */
public class LabeledStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public LabeledStatement(Token label, Statement stmt)
        {
        this.label = label;
        this.stmt  = stmt;
        }


    // ----- accessors -----------------------------------------------------------------------------


    @Override
    public long getStartPosition()
        {
        return label.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return stmt.getEndPosition();
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
        return label.getValue() + ": " + stmt;
        }

    @Override
    public String getDumpDesc()
        {
        return label.getValue() + ":";
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token     label;
    protected Statement stmt;

    private static final Field[] CHILD_FIELDS = fieldsForNames(LabeledStatement.class, "stmt");
    }
