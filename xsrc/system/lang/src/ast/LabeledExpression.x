import io.TextPosition;

import Lexer.Token;


/**
 * Represents a name (label) that is attached to another expressions, such as when an argument is
 * named.
 */
const LabeledExpression(Token label, Expression expr)
        extends Expression
    {
    @Override
    TextPosition start.get()
        {
        return label.start;
        }

    @Override
    TextPosition end.get()
        {
        return expr.end;
        }

    @Override
    String toString()
        {
        return $"{label} = {expr}";
        }
    }
