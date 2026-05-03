import io.TextPosition;

import reflect.Annotation;
import reflect.Argument;
import reflect.InvalidType;


/**
 * Represents an annotation, including its arguments if any.
 */
const AnnotationExpression(TypeExpression name,
                           Expression[]?  args,
                           TextPosition   start,
                           TextPosition   end)
        extends Expression {
    /**
     * Assemble the type that results from the application of an annotation to an underlying type.
     *
     * @param typeSystem      the `TypeSystem` to use to resolve type and class names
     * @param hideExceptions  pass True to catch type exceptions and return them as `False` instead
     *
     * @return True iff the annotation could be successfully resolved
     * @return (conditional) the annotation
     *
     * @throws InvalidType  if a type exception occurs and `hideExceptions` is not specified
     */
    conditional Annotation resolveAnnotation(TypeSystem typeSystem, Boolean hideExceptions = False) {
        Class annoClass;
        if (Type annoType  := name.resolveType(typeSystem, hideExceptions),
                 annoClass := annoType.fromClass()) {} else {
            return False;
        }

        // build a reflect.Argument for each annotation arg
        Argument[] values = [];
        if (args != Null && !args.empty) {
            values = new Argument[];
            Loop: for (Expression expr : args) {
                if (immutable Const value := expr.hasConstantValue()) {
                    // REVIEW does this need to specify the Referent type?
                    values[Loop.count] = new Argument(value);
                } else {
                    return False;
                }
            }
        }

        try {
            // create the annotation
            return True, new Annotation(annoClass, values);
        } catch (InvalidType e) {
            if (hideExceptions) {
                return False;
            }
            throw e;
        }
    }

    @Override
    String toString() {
        val args = this.args;
        if (args == Null) {
            return $"@{name}";
        }
        val argList = args.toString(sep=", ", pre="(", post=")");
        return $"@{name}{argList}";
    }
}
