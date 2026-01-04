package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * Base class for all declaration nodes in the green tree.
 * <p>
 * Declarations introduce new symbols (classes, methods, properties, etc.).
 */
public abstract sealed class GreenDeclaration extends GreenNode
        permits GreenClassDecl, GreenMethodDecl, GreenPropertyDecl, GreenModuleDecl {

    /**
     * Construct a declaration node.
     *
     * @param kind      the syntax kind (must be a declaration kind)
     * @param fullWidth the total character width
     */
    protected GreenDeclaration(SyntaxKind kind, int fullWidth) {
        super(kind, fullWidth);
        assert kind.isDeclaration() : "Not a declaration kind: " + kind;
    }

    @Override
    public <R> R accept(GreenVisitor<R> visitor) {
        return visitor.visitDeclaration(this);
    }
}
