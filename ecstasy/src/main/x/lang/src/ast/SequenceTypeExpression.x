import src.Lexer.Token;

import reflect.InvalidType;


/**
 * Represents a Sequence type, such as:
 *
 *     String...
 */
const SequenceTypeExpression(TypeExpression type, Token suffix)
        extends SuffixTypeExpression(type, suffix)
    {
    @Override
    conditional Type resolveType(TypeSystem typeSystem, Boolean hideExceptions = False)
        {
        if (Type elementType := type.resolveType(typeSystem, hideExceptions))
            {
            try
                {
                return True, Sequence.toType().parameterize([elementType]);
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
