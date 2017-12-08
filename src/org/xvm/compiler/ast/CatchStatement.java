package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import static org.xvm.util.Handy.indentLines;


/**
 * A "catch" statement. (Not actually a statement. It only occurs within a try.)
 */
public class CatchStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public CatchStatement(VariableDeclarationStatement target, StatementBlock block, long lStartPos)
        {
        this.target    = target;
        this.block     = block;
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
        return "catch (" + target + ")\n" + indentLines(block.toString(), "    ");
        }


    // ----- fields --------------------------------------------------------------------------------

    protected VariableDeclarationStatement target;
    protected StatementBlock               block;
    protected long                         lStartPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(CatchStatement.class, "target", "block");
    }
