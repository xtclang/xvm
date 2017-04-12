package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.util.Collections;
import java.util.Map;


/**
 * A name expression specifies a name; this is a special kind of name that no one cares about. The
 * ignored name expression is used as a lambda parameter when nobody cares what the parameter is
 * and they just want it to go away quietly.
 *
 * @author cp 2017.03.28
 */
public class IgnoredNameExpression
        extends NameExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public IgnoredNameExpression(Token name)
        {
        super(name);
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public TypeExpression toTypeExpression()
        {
        return new BadTypeExpression(this);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return "_";
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }

    @Override
    public Map<String, Object> getDumpChildren()
        {
        return Collections.EMPTY_MAP;
        }
    }
