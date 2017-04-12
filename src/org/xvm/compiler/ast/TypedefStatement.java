package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import org.xvm.util.ListMap;

import java.util.Map;


/**
 * A typedef statement specifies a type to alias as a simple name.
 *
 * @author cp 2017.03.28
 */
public class TypedefStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public TypedefStatement(Token alias, TypeExpression type)
        {
        this.alias = alias;
        this.type  = type;
        }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return "typedef " + type + " " + alias.getValue() + ';';
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }

    @Override
    public Map<String, Object> getDumpChildren()
        {
        ListMap<String, Object> map = new ListMap();
        map.put("type", type);
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token          alias;
    protected TypeExpression type;
    }
