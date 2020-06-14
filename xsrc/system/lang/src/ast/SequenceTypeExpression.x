import Lexer.Token;


/**
 * Represents a Sequence type, such as:
 *
 *     String...
 */
const SequenceTypeExpression(TypeExpression type, Token suffix)
        extends SuffixTypeExpression(type, suffix);
