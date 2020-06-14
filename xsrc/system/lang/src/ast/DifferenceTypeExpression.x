import Lexer.Token;


/**
 * Represents a difference type expressions, such as:
 *
 *     HashMap - Map
 */
const DifferenceTypeExpression(TypeExpression left, Token operator, TypeExpression right)
        extends RelationalTypeExpression(left, operator, right);
