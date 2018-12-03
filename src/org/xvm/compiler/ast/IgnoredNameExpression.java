package org.xvm.compiler.ast;


import java.util.Collections;
import java.util.Map;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.compiler.Token;


/**
 * A name expression specifies a name; this is a special kind of name that no one cares about. The
 * ignored name expression is used as a lambda parameter when nobody cares what the parameter is
 * and they just want it to go away quietly.
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

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        return pool().typeObject();
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
