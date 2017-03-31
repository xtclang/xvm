package org.xvm.compiler.ast;


import org.xvm.compiler.Token;

/**
 * An bi type expression is a type expression composed of two type expressions.
 *
 * @author cp 2017.03.31
 */
public class BiTypeExpression
        extends TypeExpression
    {
    public BiTypeExpression(TypeExpression type1, Token operator, TypeExpression type2)
        {
        this.type1    = type1;
        this.operator = operator;
        this.type2    = type2;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(type1)
          .append(' ')
          .append(operator.getId().TEXT)
          .append(' ')
          .append(type2);

        return sb.toString();
        }

    public final TypeExpression type1;
    public final Token operator;
    public final TypeExpression type2;
    }
