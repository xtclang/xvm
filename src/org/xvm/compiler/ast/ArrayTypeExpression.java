package org.xvm.compiler.ast;


/**
 * An array type expression is a type expression followed by an array indicator.
 *
 * @author cp 2017.03.31
 */
public class ArrayTypeExpression
        extends TypeExpression
    {
    public ArrayTypeExpression(TypeExpression type)
        {
        this.type       = type;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(type)
          .append("[]");

        return sb.toString();
        }

    public final TypeExpression type;
    }
