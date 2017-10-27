package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;
import java.util.List;


/**
 * This is a complicated form of the variable declaration statement that allows for multiple
 * L-values to be assigned, of which any number can be a new variable declaration.
 */
public class MultipleDeclarationStatement
        extends ConditionalStatement
    {
    // ----- constructors --------------------------------------------------------------------------

    public MultipleDeclarationStatement(List<Statement> lvalues, Expression rvalue, long lStartPos)
        {
        this.lvalues   = lvalues;
        this.rvalue    = rvalue;
        this.lStartPos = lStartPos;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return lStartPos;
        }

    @Override
    public long getEndPosition()
        {
        return rvalue.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- ConditionalStatement methods ----------------------------------------------------------

    @Override
    protected void split()
        {
        // TODO for now pretend that this only declares but does not assign
        long      lPos    = getEndPosition();
        Statement stmtNOP = new StatementBlock(Collections.EMPTY_LIST, lPos, lPos);
        configureSplit(this, stmtNOP);
        }


    // ----- compilation ---------------------------------------------------------------------------

    // TODO


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
    protected long            lStartPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(MultipleDeclarationStatement.class, "lvalues", "rvalue");
    }
