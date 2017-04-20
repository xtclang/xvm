package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.lang.reflect.Field;

import java.util.List;

import static org.xvm.util.Handy.indentLines;


/**
 * An "Iterable"-based "for" statement.
 *
 * @author cp 2017.04.11
 */
public class ForStatement2
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public ForStatement2(Token keyword, List<Statement> conds, StatementBlock block)
        {
        this.keyword = keyword;
        this.conds   = conds;
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
        StringBuilder sb = new StringBuilder();

        sb.append(keyword.getId().TEXT)
          .append(" (");

        boolean first = true;
        for (Statement cond : conds)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append(", ");
                }

            sb.append(cond);
            }

        sb.append(")\n")
          .append(indentLines(block.toString(), "    "));

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token           keyword;
    protected List<Statement> conds;
    protected StatementBlock  block;

    private static final Field[] CHILD_FIELDS = fieldsForNames(ForStatement2.class, "conds", "block");
    }
