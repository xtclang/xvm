import io.TextPosition;

import Lexer.Token;


/**
 * Represents any type composed of a suffix on another type.
 */
@Abstract const SuffixTypeExpression(TypeExpression type, Token suffix)
        extends TypeExpression {
    @Override
    TextPosition start.get() {
        return type.start;
    }

    @Override
    TextPosition end.get() {
        return suffix.end;
    }

    @Override
    String toString() {
        return type.is(RelationalTypeExpression)
                ? $"({type}){suffix}"
                : $"{type}{suffix}";
    }
}
