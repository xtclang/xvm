package org.xvm.compiler.ast;


import org.xvm.compiler.Token;
import org.xvm.util.ListMap;

import java.util.Map;


/**
 * A labeled statement represents a statement that has a label.
 *
 * @author cp 2017.04.09
 */
public class LabeledStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public LabeledStatement(Token label, Statement stmt)
        {
        this.label = label;
        this.stmt  = stmt;
        }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return label.getValue() + ": " + stmt;
        }

    @Override
    public String getDumpDesc()
        {
        return label.getValue() + ":";
        }

    @Override
    public Map<String, Object> getDumpChildren()
        {
        ListMap<String, Object> map = new ListMap();
        map.put("stmt", stmt);
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token     label;
    protected Statement stmt;
    }
