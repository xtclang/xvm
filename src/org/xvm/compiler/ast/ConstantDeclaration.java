package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import java.util.Map;

import static org.xvm.util.Handy.appendString;


/**
 * A constant declaration statement specifies a constant value associated with a simple name.
 */
public class ConstantDeclaration
        extends VariableDeclarationStatement
    {
    // ----- constructors --------------------------------------------------------------------------

    public ConstantDeclaration(Token keyword, TypeExpression type, Token name, Expression value, Token doc)
        {
        super(type, name, value);
        this.keyword = keyword;
        this.doc     = doc;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return keyword.getStartPosition();
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        if (doc != null)
            {
            String sDoc = String.valueOf(doc.getValue());
            if (sDoc.length() > 100)
                {
                sDoc = sDoc.substring(0, 97) + "...";
                }
            appendString(sb.append("/*"), sDoc).append("*/\n");
            }

        sb.append("static ")
          .append(super.toString());

        return sb.toString();
        }

    @Override
    public Map<String, Object> getDumpChildren()
        {
        Map<String, Object> map = super.getDumpChildren();
        map.put("keyword", keyword);
        return map;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token keyword;
    protected Token doc;
    }
