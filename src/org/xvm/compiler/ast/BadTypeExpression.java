package org.xvm.compiler.ast;


import org.xvm.util.ListMap;

import java.util.Map;


/**
 * A type expression that can't figure out how to be a type exception. It pretends to be a type,
 * but it's going to end in misery and compiler errors.
 *
 * @author cp 2017.04.07
 */
public class BadTypeExpression
        extends TypeExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public BadTypeExpression(Expression nonType)
        {
        this.nonType = nonType;
        }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return "/* NOT A TYPE!!! */ " + nonType;
        }

    @Override
    public Map<String, Object> getDumpChildren()
        {
        ListMap<String, Object> map = new ListMap();
        map.put("nonType", nonType);
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression nonType;
    }
