package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;


/**
 * This is a complicated form of the variable declaration statement that allows for multiple
 * L-values to be assigned, of which any number can be a new variable declaration.
 *
 * @author cp 2017.04.10
 */
public class MultipleDeclarationStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public MultipleDeclarationStatement(List<Statement> lvalues, Expression rvalue)
        {
        this.lvalues = lvalues;
        this.rvalue  = rvalue;
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

        sb.append('(');

        boolean first = true;
        for (Statement stmt : lvalues)
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

        sb.append(" = ")
          .append(rvalue)
          .append(';');

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected List<Statement> lvalues;
    protected Expression      rvalue;

    private static final Field[] CHILD_FIELDS = fieldsForNames(MultipleDeclarationStatement.class, "lvalues", "rvalue");
    }
