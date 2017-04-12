package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import org.xvm.util.ListMap;

import java.util.List;
import java.util.Map;

import static org.xvm.util.Handy.indentLines;


/**
 * The traditional "for" statement.
 *
 * @author cp 2017.04.10
 */
public class ForStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public ForStatement(Token keyword, List<Statement> init, Expression expr,
                        List<Statement> update, StatementBlock block)
        {
        this.keyword = keyword;
        this.init    = init;
        this.expr    = expr;
        this.update  = update;
        this.block   = block;
        }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

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

    @Override
    public Map<String, Object> getDumpChildren()
        {
        ListMap<String, Object> map = new ListMap();
        map.put("init", init);
        map.put("expr", expr);
        map.put("update", update);
        map.put("block", block);
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token           keyword;
    protected List<Statement> init;
    protected Expression      expr;
    protected List<Statement> update;
    protected StatementBlock  block;
    }
