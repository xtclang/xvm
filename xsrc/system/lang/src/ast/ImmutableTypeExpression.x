import Lexer.Token;


/**
 * Represents an immutable type, such as:
 *
 *     immutable Int[]
 */
const ImmutableTypeExpression(Token prefix, TypeExpression type)
        extends PrefixTypeExpression(prefix, type)
    {
    @Override
    String toString()
        {
        return type.is(RelationalTypeExpression)
                ? $"immutable ({type})"
                : $"immutable {type}";
        }
    }
