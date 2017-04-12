package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.util.List;


/**
 * An import statement specifies a qualified name to alias as a simple name.
 *
 * @author cp 2017.03.28
 */
public class ImportStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public ImportStatement(Token alias, List<Token> qualifiedName)
        {
        this.alias         = alias;
        this.qualifiedName = qualifiedName;
        }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        boolean first = true;
        String last = null;
        for (Token name : qualifiedName)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append('.');
                }
            last = String.valueOf(name.getValue());
            sb.append(last);
            }

        if (alias != null && !last.equals(alias.getValue()))
            {
            sb.append(" as ")
              .append(alias.getValue());
            }

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token       alias;
    protected List<Token> qualifiedName;
    }
