package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.util.List;

import static org.xvm.util.Handy.indentLines;


/**
 * An iteration-based "for" statement.
 *
 * @author cp 2017.04.11
 */
public class ForStatement2
        extends Statement
    {
    public ForStatement2(Token keyword, List<Statement> conds, StatementBlock block)
        {
        this.keyword = keyword;
        this.conds   = conds;
        this.block   = block;
        }

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

    public final Token           keyword;
    public final List<Statement> conds;
    public final StatementBlock  block;
    }
