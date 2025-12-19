package org.xvm.compiler.ast;


import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Objects;
import java.util.function.Function;


/**
 * Abstract base class for type expressions that have a single TypeExpression child.
 */
public abstract class UnaryTypeExpression
        extends TypeExpression {
    // ----- constructors --------------------------------------------------------------------------

    protected UnaryTypeExpression(@NotNull TypeExpression type) {
        this.type = Objects.requireNonNull(type);
    }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the underlying type expression
     */
    public TypeExpression getUnderlyingType() {
        return type;
    }

    @Override
    protected boolean canResolveNames() {
        return super.canResolveNames() || type.canResolveNames();
    }

    @Override
    public <T> T forEachChild(Function<AstNode, T> visitor) {
        return visitor.apply(type);
    }

    @Override
    public List<AstNode> children() {
        return List.of(type);
    }

    @Override
    protected void replaceChild(AstNode oldChild, AstNode newChild) {
        assertReplaced(tryReplace(oldChild, newChild, type, n -> type = n), oldChild);
    }


    // ----- fields --------------------------------------------------------------------------------

    protected TypeExpression type;
}
