import io.TextPosition;

import Lexer.Token;


/**
 * Represents an expression composed of a unary operator preceding another expression.
 */
const UnaryExpression(Token op, Expression expr)
        extends Expression
    {
    @Override
    TextPosition start.get()
        {
        return op.start;
        }

    @Override
    TextPosition end.get()
        {
        return expr.end;
        }

    @Override
    String toString()
        {
        return $"{op}{expr}";
        }
    }
