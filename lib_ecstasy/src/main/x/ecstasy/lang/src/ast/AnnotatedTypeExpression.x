import io.TextPosition;

import reflect.Annotation;
import reflect.InvalidType;


/**
 * Represents an annotated type, for example:
 *
 *     @Unchecked Int64
 */
const AnnotatedTypeExpression(AnnotationExpression annotation,
                              TypeExpression       type)
        extends TypeExpression {
    @Override
    TextPosition start.get() {
        return annotation.start;
    }

    @Override
    TextPosition end.get() {
        return type.end;
    }

    @Override
    conditional Type resolveType(TypeSystem typeSystem, Boolean hideExceptions = False) {
        // determine the type that is going to be annotated
        if (Type       annotatee  := type.resolveType(typeSystem, hideExceptions),
            Annotation annotation := this.annotation.resolveAnnotation(typeSystem, hideExceptions)) {
            try {
                return True, annotatee.annotate(annotation);
            } catch (InvalidType e) {
                if (hideExceptions) {
                    return False;
                }

                throw e;
            }
        }

        return False;
    }

    @Override
    String toString() {
        return type.is(RelationalTypeExpression)
                ? $"{annotation} ({type})"
                : $"{annotation} {type}";
    }
}