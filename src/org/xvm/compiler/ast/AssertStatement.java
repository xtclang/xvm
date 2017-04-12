package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import org.xvm.util.ListMap;

import java.util.Map;


/**
 * An assert statement.
 *
 * @author cp 2017.04.09
 */
public class AssertStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public AssertStatement(Token keyword, Token suffix, Statement stmt)
        {
        this.keyword = keyword;
        this.suffix  = suffix;
        this.stmt    = stmt;
        }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(keyword.getId().TEXT);
        if (suffix != null)
            {
            sb.append(':')
              .append(suffix);
            }

        if (stmt != null)
            {
            sb.append(' ')
              .append(stmt);
            }

        return sb.toString();
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
        map.put("keyword", keyword);
        map.put("suffix", suffix);
        map.put("stmt", stmt);
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token keyword;
    protected Token suffix;
    protected Statement stmt;
    }
