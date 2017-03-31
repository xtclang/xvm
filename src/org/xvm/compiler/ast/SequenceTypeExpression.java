package org.xvm.compiler.ast;


/**
 * An sequence type expression is a type expression followed by an ellipsis.
 *
 * @author cp 2017.03.31
 */
public class SequenceTypeExpression
        extends TypeExpression
    {
    public SequenceTypeExpression(TypeExpression type)
        {
        this.type = type;
        }

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(type)
          .append("...");

        return sb.toString();
        }

    public final TypeExpression type;
    }
