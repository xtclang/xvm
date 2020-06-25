import io.TextPosition;

import Lexer.Token;


/**
 * Represents a literal value in the source code.
 */
const LiteralExpression(Token value)
        extends Expression
    {
    @Override
    TextPosition start.get()
        {
        return value.start;
        }

    @Override
    TextPosition end.get()
        {
        return value.end;
        }

    @Override
    String toString()
        {
        return value.toString();
        }
    }
