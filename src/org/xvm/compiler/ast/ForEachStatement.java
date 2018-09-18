package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.op.Label;

import org.xvm.compiler.Token;

import static org.xvm.util.Handy.indentLines;


/**
 * An "Iterable"-based "for" statement.
 */
public class ForEachStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public ForEachStatement(Token keyword, AssignmentStatement cond, StatementBlock block)
        {
        this.keyword = keyword;
        this.cond = cond;
        this.block   = block;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean canBreak()
        {
        return true;
        }

    @Override
    public boolean canContinue()
        {
        return true;
        }

    @Override
    public Label getContinueLabel()
        {
        Label label = m_labelContinue;
        if (label == null)
            {
            m_labelContinue = label = new Label("continue_foreach_" + (++s_nLabelCounter));
            }
        return label;
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
        StringBuilder sb = new StringBuilder();

        sb.append(keyword.getId().TEXT)
          .append(" (")
          .append(cond)
          .append(")\n")
          .append(indentLines(block.toString(), "    "));

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token               keyword;
    protected AssignmentStatement cond;
    protected StatementBlock      block;

    private static int   s_nLabelCounter;
    private        Label m_labelContinue;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ForEachStatement.class, "cond", "block");
    }
