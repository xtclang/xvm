package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import java.util.List;

import org.xvm.compiler.Token;

import static org.xvm.util.Handy.indentLines;


/**
 * A "switch" statement.
 */
public class SwitchStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public SwitchStatement(Token keyword, List<AstNode> cond, StatementBlock block)
        {
        this.keyword = keyword;
        this.cond    = cond;
        this.block   = block;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public boolean isNaturalShortCircuitStatementTarget()
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
        StringBuilder sb = new StringBuilder();
        sb.append("switch (");

        if (cond != null)
            {
            sb.append(cond.get(0));
            for (int i = 1, c = cond.size(); i < c; ++i)
                {
                sb.append(", ")
                  .append(cond.get(i));
                }
            }

        sb.append(")\n")
          .append(indentLines(block.toString(), "    "));

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token          keyword;
    protected List<AstNode>  cond;
    protected StatementBlock block;

    private static final Field[] CHILD_FIELDS = fieldsForNames(SwitchStatement.class, "cond", "block");
    }
