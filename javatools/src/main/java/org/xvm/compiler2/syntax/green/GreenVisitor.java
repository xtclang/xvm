package org.xvm.compiler2.syntax.green;

/**
 * Visitor interface for green nodes.
 *
 * @param <R> the result type
 */
public interface GreenVisitor<R> {

    /**
     * Visit a token node.
     */
    R visitToken(GreenToken token);

    /**
     * Visit an expression node.
     */
    R visitExpression(GreenExpression expression);

    /**
     * Visit a statement node.
     */
    R visitStatement(GreenStatement statement);

    /**
     * Visit a declaration node.
     */
    R visitDeclaration(GreenDeclaration declaration);

    /**
     * Visit a type node.
     */
    R visitType(GreenType type);

    /**
     * Visit a list node.
     */
    R visitList(GreenList list);
}
