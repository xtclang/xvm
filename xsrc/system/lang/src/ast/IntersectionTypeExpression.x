import Lexer.Token;


/**
 * Represents an intersection type expressions, such as:
 *
 *     Nullable | String
 */
const IntersectionTypeExpression(TypeExpression left, Token operator, TypeExpression right)
        extends RelationalTypeExpression(left, operator, right);
