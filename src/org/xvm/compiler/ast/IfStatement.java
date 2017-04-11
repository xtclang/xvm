package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import static org.xvm.util.Handy.indentLines;


/**
 * An "if" statement.
 *
 * @author cp 2017.04.10
 */
public class IfStatement
        extends Statement
    {
    public IfStatement(Token keyword, Statement cond, StatementBlock block)
        {
        this(keyword, cond, block, null);
        }

    public IfStatement(Token keyword, Statement cond, StatementBlock stmtThen, Statement stmtElse)
        {
        this.keyword   = keyword;
        this.cond      = cond;
        this.stmtThen = stmtThen;
        this.stmtElse = stmtElse;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("if (")
          .append(cond)
          .append(")\n")
          .append(indentLines(stmtThen.toString(), "    "));

        if (stmtElse != null)
            {
            if (stmtElse instanceof IfStatement)
                {
                sb.append("\nelse ")
                  .append(stmtElse);
                }
            else
                {
                sb.append("\nelse\n")
                  .append(indentLines(stmtElse.toString(), "    "));
                }
            }

        return sb.toString();
        }

    public final Token     keyword;
    public final Statement cond;
    public final Statement stmtThen;
    public final Statement stmtElse;
    }
