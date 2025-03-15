import io.TextPosition;

import reflect.Annotation;
import reflect.InvalidType;


/**
 * Represents an annotated type, for example:
 *
 *     @AutoFreezable Account
 */
const AnnotatedTypeExpression(AnnotationExpression anno, TypeExpression type)
        extends TypeExpression {
    @Override
    TextPosition start.get() = anno.start;

    @Override
    TextPosition end.get() = type.end;

    @Override
    conditional Type resolveType(TypeSystem typeSystem, Boolean hideExceptions = False) {
        // determine the type that is going to be annotated
        if (Type       annotatee := type.resolveType(typeSystem, hideExceptions),
            Annotation anno      := this.anno.resolveAnnotation(typeSystem, hideExceptions)) {
            try {
                return True, annotatee.annotate(anno);
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
                ? $"{anno} ({type})"
                : $"{anno} {type}";
    }
}