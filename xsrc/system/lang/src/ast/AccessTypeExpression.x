import Lexer.Token;


/**
 * Represents an access type, such as:
 *
 *     Person:private
 */
const AccessTypeExpression(TypeExpression type, Token suffix)
        extends SuffixTypeExpression(type, suffix)
    {
    @Override
    String toString()
        {
        return type.is(RelationalTypeExpression)
                ? $"({type}):{suffix}"
                : $"{type}:{suffix}";
        }
    }
