package org.xvm.compiler.ast;


import org.xvm.compiler.Token;
import org.xvm.util.Handy;


/**
 * A literal expression specifies a literal value.
 *
 * @author cp 2017.03.28
 */
public class LiteralExpression
        extends Expression
    {
    public LiteralExpression(Token literal)
        {
        this.literal = literal;
        }

    @Override
    public String toString()
        {
        switch (literal.getId())
            {
            case LIT_INT:
                return String.valueOf(literal.getValue());

            case LIT_STRING:
                 return Handy.quotedString(String.valueOf(literal.getValue()));

            default:
                throw new UnsupportedOperationException("TODO");
            }
        }

    public final Token literal;
    }
