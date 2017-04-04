package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

import static org.xvm.util.Handy.appendString;


/**
 * A constant declaration statement specifies a constant value associated with a simple name.
 *
 * @author cp 2017.04.04
 */
public class ConstantDeclaration
        extends VariableDeclarationStatement
    {
    public ConstantDeclaration(Token keyword, TypeExpression type, Token name, Expression value, Token doc)
        {
        super(type, name, value);
        this.keyword = keyword;
        this.doc     = doc;
        }

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

    public final Token keyword;
    public final Token doc;
    }
