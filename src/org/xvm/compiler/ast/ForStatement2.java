package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import org.xvm.util.ListMap;

import java.util.List;
import java.util.Map;

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

    @Override
    public Map<String, Object> getDumpChildren()
        {
        ListMap<String, Object> map = new ListMap();
        map.put("conds", conds);
        map.put("block", block);
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token           keyword;
    protected List<Statement> conds;
    protected StatementBlock  block;
    }
