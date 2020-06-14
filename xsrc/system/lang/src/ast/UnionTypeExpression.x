import Lexer.Token;


/**
 * Represents a union type expressions, such as:
 *
 *     OutputStream + DataOutput
 */
const UnionTypeExpression(TypeExpression left, Token operator, TypeExpression right)
        extends RelationalTypeExpression(left, operator, right);
