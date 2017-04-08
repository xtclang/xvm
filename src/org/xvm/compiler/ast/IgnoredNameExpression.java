package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.util.Collections;


/**
 * A name expression specifies a name.
 *
 * @author cp 2017.03.28
 */
public class IgnoredNameExpression
        extends NameExpression
    {
    public IgnoredNameExpression(Token name)
        {
        super(Collections.singletonList(name));
        }

    @Override
    public TypeExpression toTypeExpression()
        {
        return new BadTypeExpression(this);
        }

    @Override
    public String toString()
        {
        return "_";
        }
    }
