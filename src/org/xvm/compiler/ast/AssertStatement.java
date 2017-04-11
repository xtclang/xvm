package org.xvm.compiler.ast;


import org.xvm.compiler.Token;


/**
 * An assert statement.
 *
 * @author cp 2017.04.09
 */
public class AssertStatement
        extends Statement
    {
    public AssertStatement(Token keyword, Token suffix, Statement stmt)
        {
        this.keyword = keyword;
        this.suffix  = suffix;
        this.stmt    = stmt;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(keyword.getId().TEXT);
        if (suffix != null)
            {
            sb.append(':')
              .append(suffix);
            }

        if (stmt != null)
            {
            sb.append(' ')
              .append(stmt);
            }

        return sb.toString();
        }

    public final Token keyword;
    public final Token suffix;
    public final Statement stmt;
    }
