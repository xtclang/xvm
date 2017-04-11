package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.util.List;

import static org.xvm.util.Handy.indentLines;


/**
 * The traditional "for" statement.
 *
 * @author cp 2017.04.10
 */
public class ForStatement
        extends Statement
    {
    public ForStatement(Token keyword, List<Statement> init, Expression expr,
                        List<Statement> update, StatementBlock block)
        {
        this.keyword = keyword;
        this.init    = init;
        this.expr    = expr;
        this.update  = update;
        this.block   = block;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append("for (");
        
        if (init != null)
            {
            boolean first = true;
            for (Statement stmt : init)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(stmt);
                }
            }

        sb.append("; ");

        if (expr != null)
            {
            sb.append(expr);
            }

        sb.append("; ");

        if (update != null)
            {
            boolean first = true;
            for (Statement stmt : update)
                {
                if (first)
                    {
                    first = false;
                    }
                else
                    {
                    sb.append(", ");
                    }
                sb.append(stmt);
                }
            }

        sb.append(")\n")
          .append(indentLines(block.toString(), "    "));

        return sb.toString();
        }

    public final Token           keyword;
    public final List<Statement> init;
    public final Expression      expr;
    public final List<Statement> update; 
    public final StatementBlock  block;
    }
