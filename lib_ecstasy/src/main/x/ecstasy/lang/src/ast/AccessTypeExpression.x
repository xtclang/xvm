import src.Lexer.Token;

import reflect.Access;
import reflect.InvalidType;


/**
 * Represents an access type, such as:
 *
 *     Person:private
 */
const AccessTypeExpression(TypeExpression type, Token suffix)
        extends SuffixTypeExpression(type, suffix) {
    @Override
    String toString() {
        return type.is(RelationalTypeExpression)
                ? $"({type}):{suffix}"
                : $"{type}:{suffix}";
    }

    /**
     * The [Access] specified by this `AccessTypeExpression`.
     */
    Access access.get() {
        return switch (suffix.id) {
            case Public   : Public;
            case Protected: Protected;
            case Private  : Private;
            case Struct   : Struct;
            default       : assert;
        };
    }

    @Override
    conditional Type resolveType(TypeSystem typeSystem, Boolean hideExceptions = False) {
        if (Type result := type.resolveType(typeSystem, hideExceptions)) {
            // check for previously existing access specification
            if (Access previousAccess := result.accessSpecified()) {
                // if it matches, then the work here is already done
                if (previousAccess == this.access) {
                    return True, result;
                }

                // try to strip off the previous access
                if (Type stripped := result.modifying(), !result.accessSpecified()) {
                    result = stripped;
                } else {
                    if (hideExceptions) {
                        return False;
                    }

                    throw new InvalidType(
                            $"Unable to remove {previousAccess.keyword} access from type: {result}");
                }
            }

            // apply the specified access modifier
            switch (suffix.id) {
            case Public:
                return True, result;

            case Protected: {
                if (Class clz := result.fromClass()) {
                    return True, clz.ProtectedType;
                }
                return False;
            }

            case Private: {
                if (Class clz := result.fromClass()) {
                    return True, clz.PrivateType;
                }
                return False;
            }

            case Struct: {
                if (Class clz := result.fromClass()) {
                    return True, clz.StructType;
                }
                return False;
            }

            default: assert;
            }
        }

        return False;
    }
}
