package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import org.xvm.util.ListMap;

import java.util.List;
import java.util.Map;


/**
 * An import statement specifies a qualified name to alias as a simple name.
 *
 * @author cp 2017.03.28
 */
public class ImportStatement
        extends Statement
    {
    // ----- constructors --------------------------------------------------------------------------

    public ImportStatement(Expression cond, Token alias, List<Token> qualifiedName)
        {
        this.cond          = cond;
        this.alias         = alias;
        this.qualifiedName = qualifiedName;
        }


    // ----- accessors -----------------------------------------------------------------------------


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (cond != null)
            {
            sb.append("if (")
              .append(cond)
              .append(") { ");
            }

        sb.append("import ");

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

        sb.append(';');

        if (cond != null)
            {
            sb.append(" }");
            }

        return sb.toString();
        }

    @Override
    public Map<String, Object> getDumpChildren()
        {
        ListMap<String, Object> map = new ListMap();
        map.put("cond", cond);
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression  cond;
    protected Token       alias;
    protected List<Token> qualifiedName;
    }
