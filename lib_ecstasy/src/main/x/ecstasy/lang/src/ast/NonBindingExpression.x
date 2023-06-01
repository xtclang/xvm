import io.TextPosition;


/**
 * Represents a return value whose value is not required.
 */
const NonBindingExpression(TypeExpression? type,
                           TextPosition    start,
                           TextPosition    end)
        extends Expression {
    @Override
    String toString() {
        return type == Null
                ? "_"
                : $"<{type}> _";
    }
}
