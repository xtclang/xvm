import io.TextPosition;

import Lexer.Token;


/**
 * A representation of types compomsed of a prefix token in front of a type.
 */
@Abstract const PrefixTypeExpression(Token prefix, TypeExpression type)
        extends TypeExpression
    {
    @Override
    TextPosition start.get()
        {
        return prefix.start;
        }

    @Override
    TextPosition end.get()
        {
        return type.end;
        }

    @Override
    String toString()
        {
        return type.is(RelationalTypeExpression)
                ? $"{prefix} ({type})"
                : $"{prefix} {type}";
        }
    }
