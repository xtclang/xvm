import io.TextPosition;

import Lexer.Token;


/**
 * Represents the type of a function, including the parameter types and the return types.
 */
const FunctionTypeExpression(Token             func,
                             Parameter[]       returns,
                             TypeExpression[]  params,
                             TextPosition      end)
        extends TypeExpression {

    @Override
    TextPosition start.get() {
        return func.start;
    }

    @Override
    String toString() {
        val ret = switch (returns.size) {
            case 0:  "void";
            case 1:  returns[0].name == Null ? returns[0].toString() : $"({returns[0]})";
            default: returns.toString(sep=", ", pre="(", post=")");
        };
        val args = params.toString(sep=", ", pre="(", post=")");
        return $"function {ret} {args}";
    }
}
