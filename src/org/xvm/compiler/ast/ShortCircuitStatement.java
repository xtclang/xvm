package org.xvm.compiler.ast;


import org.xvm.compiler.Token;


/**
 * A short-cirtuit statement represents "break" and "continue" statements.
 *
 * @author cp 2017.04.09
 */
public class ShortCircuitStatement
        extends Statement
    {
    public ShortCircuitStatement(Token keyword, Token name)
        {
        this.keyword = keyword;
        this.name    = name;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(keyword.getId().TEXT);

        if (name != null)
            {
            sb.append(' ')
              .append(name.getValue());
            }

        sb.append(';');
        return sb.toString();
        }

    public final Token keyword;
    public final Token name;
    }
