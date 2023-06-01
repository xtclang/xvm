import io.TextPosition;

import Lexer.Token;


/**
 * Represents all expressions that represents the combination, using an operator, of two types.
 */
@Abstract const RelationalTypeExpression(TypeExpression left, Token operator, TypeExpression right)
        extends TypeExpression {

    @Override
    TextPosition start.get() {
        return left.start;
    }

    @Override
    TextPosition end.get() {
        return right.end;
    }

    @Override
    String toString() {
        return right.is(RelationalTypeExpression)
                ? $"{left} {operator} ({right})"
                : $"{left} {operator} {right}";
    }
}
