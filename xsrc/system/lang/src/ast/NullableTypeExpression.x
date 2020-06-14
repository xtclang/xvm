import Lexer.Token;


/**
 * Represents a Nullable type, such as:
 *
 *     String?
 */
const NullableTypeExpression(TypeExpression type, Token suffix)
        extends SuffixTypeExpression(type, suffix);
