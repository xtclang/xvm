package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.compiler.Token;

import static org.xvm.util.Handy.indentLines;


/**
 * A "switch" statement.
 */
public class SwitchStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public SwitchStatement(Token keyword, Statement cond, StatementBlock block)
        {
        this.keyword = keyword;
        this.cond    = cond;
        this.block   = block;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean canBreak()
        {
        return true;
        }

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return block.getEndPosition();
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
        return "switch (" + (cond == null ? "" : cond) + ")\n"
                + indentLines(block.toString(), "    ");
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token          keyword;
    protected Statement      cond;
    protected StatementBlock block;

    private static final Field[] CHILD_FIELDS = fieldsForNames(SwitchStatement.class, "cond", "block");
    }
