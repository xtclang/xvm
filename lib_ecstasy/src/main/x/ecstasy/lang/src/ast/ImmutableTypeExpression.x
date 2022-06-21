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
    conditional Type resolveType(TypeSystem typeSystem, Boolean hideExceptions = False)
        {
        if (Type type := type.resolveType(typeSystem, hideExceptions))
            {
            return True, (immutable).and(type);
            }
        return False;
        }

    @Override
    String toString()
        {
        return type.is(RelationalTypeExpression)
                ? $"immutable ({type})"
                : $"immutable {type}";
        }
    }