import io.TextPosition;

import reflect.InvalidType;


/**
 * Represents an annotated type, for example:
 *
 *     Int[]
 */
const ArrayTypeExpression(TypeExpression type,
                          Int            dims,
                          TextPosition   end)
        extends TypeExpression {
    @Override
    TextPosition start.get() {
        return type.start;
    }

    @Override
    conditional Type resolveType(TypeSystem typeSystem, Boolean hideExceptions = False) {
        if (Type elementType := type.resolveType(typeSystem, hideExceptions)) {
            try {
                switch (dims) {
                case 0:     // e.g. "Int[]"
                case 1:     // e.g. "Int[?]"
                    return True, Array.toType().parameterize([elementType]);

                case 2:     // e.g. "Int[?,?]
                    return True, Matrix.toType().parameterize([elementType]);
                }
            } catch (InvalidType e) {
                if (!hideExceptions) {
                    throw e;
                }
            }
        }

        return False;
    }

    @Override
    String toString() {
        val marks = new Array<String>(dims, "?").toString(sep=",", pre="", post="");
        return type.is(RelationalTypeExpression)
                ? $"({type})[{marks}]"
                : $"{type}[{marks}]";
    }
}
