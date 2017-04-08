package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.util.ArrayList;
import java.util.List;


/**
 * A name expression specifies a name.
 *
 * @author cp 2017.03.28
 */
public class NameExpression
        extends Expression
    {
    public NameExpression(Token name)
        {
        names = new ArrayList<>(3);
        names.add(name);
        }

    public NameExpression(List<Token> names)
        {
        this.names = names;
        }

    @Override
    public TypeExpression toTypeExpression()
        {
        return new NamedTypeExpression(null, names, null);
        }

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

    public final List<Token> names;
    }
