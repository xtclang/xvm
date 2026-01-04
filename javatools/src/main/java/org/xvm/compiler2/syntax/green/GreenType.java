package org.xvm.compiler2.syntax.green;

import org.xvm.compiler2.syntax.SyntaxKind;

/**
 * Base class for all type expression nodes in the green tree.
 * <p>
 * Type expressions represent types in syntax (Int, String, List&lt;Int&gt;, etc.).
 */
public abstract sealed class GreenType extends GreenNode
        permits GreenNamedType, GreenParameterizedType, GreenArrayType, GreenNullableType {

    /**
     * Construct a type node.
     *
     * @param kind      the syntax kind (must be a type kind)
     * @param fullWidth the total character width
     */
    protected GreenType(SyntaxKind kind, int fullWidth) {
        super(kind, fullWidth);
        assert kind.isType() : "Not a type kind: " + kind;
    }

    @Override
    public <R> R accept(GreenVisitor<R> visitor) {
        return visitor.visitType(this);
    }
}
