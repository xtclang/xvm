package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import static org.xvm.util.Handy.indentLines;


/**
 * A "while" or "do while" statement.
 *
 * @author cp 2017.04.09
 */
public class WhileStatement
        extends Statement
    {
    public WhileStatement(Token keyword, Statement cond, StatementBlock block)
        {
        this.keyword = keyword;
        this.cond    = cond;
        this.block   = block;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (keyword.getId() == Token.Id.WHILE || keyword.getId() == Token.Id.FOR)
            {
            sb.append(keyword.getId().TEXT)
              .append(" (");

            sb.append(cond)
              .append(")\n");

            sb.append(indentLines(block.toString(), "    "));
            }
        else
            {
            assert keyword.getId() == Token.Id.DO;

            sb.append("do")
              .append('\n')
              .append(indentLines(block.toString(), "    "))
              .append("\nwhile (");

            sb.append(cond)
              .append(");");
            }

        return sb.toString();
        }

    public final Token          keyword;
    public final Statement      cond;
    public final StatementBlock block;
    }
