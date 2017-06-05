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
    // ----- constructors --------------------------------------------------------------------------

    public ShortCircuitStatement(Token keyword, Token name)
        {
        this.keyword = keyword;
        this.name    = name;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return name == null ? keyword.getEndPosition() : name.getEndPosition();
        }


    // ----- debugging assistance ------------------------------------------------------------------

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

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token keyword;
    protected Token name;
    }
