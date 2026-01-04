package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * Base class for all statement nodes in the green tree.
 * <p>
 * Statements are syntax constructs that perform actions but don't evaluate to values.
 */
public abstract sealed class GreenStatement extends GreenNode
        permits GreenBlockStmt, GreenExprStmt, GreenReturnStmt, GreenIfStmt,
                GreenWhileStmt, GreenForStmt, GreenVarStmt {

    /**
     * Construct a statement node.
     *
     * @param kind      the syntax kind (must be a statement kind)
     * @param fullWidth the total character width
     */
    protected GreenStatement(SyntaxKind kind, int fullWidth) {
        super(kind, fullWidth);
        assert kind.isStatement() : "Not a statement kind: " + kind;
    }

    @Override
    public <R> R accept(GreenVisitor<R> visitor) {
        return visitor.visitStatement(this);
    }
}
