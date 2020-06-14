import io.TextPosition;


/**
 * Represents an annotated type, for example:
 *
 *     @Unchecked Int
 */
const AnnotatedTypeExpression(AnnotationExpression annotation,
                              TypeExpression       type)
        extends TypeExpression
    {
    @Override
    TextPosition start.get()
        {
        return annotation.start;
        }

    @Override
    TextPosition end.get()
        {
        return type.end;
        }

    @Override
    String toString()
        {
        return type.is(RelationalTypeExpression)
                ? $"{annotation} ({type})"
                : $"{annotation} {type}";
        }
    }
