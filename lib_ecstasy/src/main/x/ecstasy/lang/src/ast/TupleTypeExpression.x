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
        StringBuffer buf = new StringBuffer();

        buf.add('<');
        Loop: for (TypeExpression type : types) {
            if (!Loop.first) {
                buf.add(',').add(' ');
            }
            type.appendTo(buf);
        }
        buf.add('>');

        return buf.toString();
    }
}
