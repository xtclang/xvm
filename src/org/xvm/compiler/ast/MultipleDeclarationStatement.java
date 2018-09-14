package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.Collections;
import java.util.List;

import org.xvm.asm.ErrorListener;

import org.xvm.compiler.Token;


/**
 * This is a complicated form of the variable declaration statement that allows for multiple
 * L-values to be assigned, of which any number can be a new variable declaration.
 */
public class MultipleDeclarationStatement
        extends ConditionalStatement
    {
    // ----- constructors --------------------------------------------------------------------------

    public MultipleDeclarationStatement(List<Statement> lvalues, Token op, Expression rvalue, long lStartPos)
        {
        this.lvalues   = lvalues;
        this.op        = op;
        this.rvalue    = rvalue;
        this.lStartPos = lStartPos;
        this.term      = true;
        }

    public MultipleDeclarationStatement(List<Statement> lvalues, Token op, Expression rvalue)
        {
        this.lvalues   = lvalues;
        this.op        = op;
        this.rvalue    = rvalue;
        this.lStartPos = lvalues.get(0).getStartPosition();
        this.term      = false;
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
    protected void split(Context ctx, ErrorListener errs)
        {
        // TODO for now pretend that this only declares but does not assign
        long      lPos    = getEndPosition();
        Statement stmtNOP = new StatementBlock(Collections.EMPTY_LIST, lPos, lPos);
        configureSplit(this, stmtNOP, errs);
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected boolean isRValue(Expression exprChild)
        {
        // TODO
        throw new UnsupportedOperationException("TODO");
        }

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

        sb.append(' ')
          .append(op.getId().TEXT)
          .append(' ')
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
    protected Token           op;
    protected Expression      rvalue;
    protected long            lStartPos;
    protected boolean         term;

    private static final Field[] CHILD_FIELDS = fieldsForNames(MultipleDeclarationStatement.class, "lvalues", "rvalue");
    }
