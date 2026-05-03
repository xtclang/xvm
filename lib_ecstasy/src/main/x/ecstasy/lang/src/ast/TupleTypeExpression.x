import io.TextPosition;


/**
 * Represents a type that is itself a tuple of types.
 */
const TupleTypeExpression(TypeExpression[]       types,
                          TextPosition           start,
                          TextPosition           end)
        extends TypeExpression {

    @Override
    String toString() {
        return types.toString(sep=", ", pre="<", post=">");
    }
}
