import reflect.InvalidType;

import src.Lexer.Token;


/**
 * Represents a Nullable type, such as:
 *
 *     String?
 */
const NullableTypeExpression(TypeExpression type, Token suffix)
        extends SuffixTypeExpression(type, suffix)
    {
    @Override
    conditional Type resolveType(TypeSystem typeSystem, Boolean hideExceptions = False)
        {
        if (Type type := this.type.resolveType(typeSystem, hideExceptions))
            {
            if (type.isA(Nullable))
                {
                return True, type;
                }

            try
                {
                return True, Nullable | type;
                }
            catch (InvalidType e)
                {
                if (!hideExceptions)
                    {
                    throw e;
                    }
                }
            }

        return False;
        }
    }
