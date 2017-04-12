package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.util.Collections;
import java.util.List;


/**
 * A name expression specifies a name. This handles both a simple name, a qualified name, and a name
 * with type parameters.
 *
 * @author cp 2017.03.28
 */
public class NameExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public NameExpression(Token name)
        {
        names = Collections.singletonList(name);
        }

    public NameExpression(List<Token> names, List<TypeExpression> params)
        {
        this.names = names;
        }


    // ----- accessors -----------------------------------------------------------------------------

    public boolean isSpecial()
        {
        for (Token name : names)
            {
            if (name.isSpecial())
                {
                return true;
                }
            }
        return false;
        }

    @Override
    public TypeExpression toTypeExpression()
        {
        // TODO "this:type"
        // TODO "this.ChildClass"?
        return isSpecial()
                ? new BadTypeExpression(this)
                : new NamedTypeExpression(null, names, null);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (Token token : names)
            {
            if (first)
                {
                first = false;
                }
            else
                {
                sb.append('.');
                }
            sb.append(token.getValue());
            }
        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected List<Token> names;
    }
