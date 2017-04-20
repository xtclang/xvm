package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;

import static org.xvm.util.Handy.indentLines;


/**
 * A "switch" statement.
 *
 * @author cp 2017.04.10
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
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return "switch (" + cond + ")\n" + indentLines(block.toString(), "    ");
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token          keyword;
    protected Statement      cond;
    protected StatementBlock block;

    private static final Field[] CHILD_FIELDS = fieldsForNames(SwitchStatement.class, "cond", "block");
    }
