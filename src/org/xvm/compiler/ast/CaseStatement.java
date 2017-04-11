package org.xvm.compiler.ast;


import org.xvm.compiler.Token;


/**
 * A case statement.
 *
 * @author cp 2017.04.09
 */
public class CaseStatement
        extends Statement
    {
    public CaseStatement(Token keyword, Expression expr)
        {
        this.keyword = keyword;
        this.expr    = expr;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(keyword.getId().TEXT);

        if (expr != null)
            {
            sb.append(' ')
              .append(expr);
            }

        sb.append(':');

        return sb.toString();
        }

    public final Token keyword;
    public final Expression expr;
    }
