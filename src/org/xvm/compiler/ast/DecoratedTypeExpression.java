package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

/**
 * A decorated type expression is a type expression preceded by a keyword that adjusts the meaning
 * of the type expression.
 *
 * @author cp 2017.04.04
 */
public class DecoratedTypeExpression
        extends TypeExpression
    {
    public DecoratedTypeExpression(Token keyword, TypeExpression type)
        {
        this.keyword = keyword;
        this.type    = type;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(keyword.getId().TEXT)
          .append(type);

        return sb.toString();
        }

    public final Token          keyword;
    public final TypeExpression type;
    }
