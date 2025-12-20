package org.xvm.compiler.ast;


import java.util.List;
import java.util.function.Function;

import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;


/**
 * A decorated type expression is a type expression preceded by a keyword that adjusts the meaning
 * of the type expression.
 */
public class DecoratedTypeExpression
        extends TypeExpression {
    // ----- constructors --------------------------------------------------------------------------

    public DecoratedTypeExpression(Token keyword, TypeExpression type) {
        this.keyword = keyword;
        this.type    = type;
    }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    protected boolean canResolveNames() {
        return super.canResolveNames() || type.canResolveNames();
    }

    @Override
    public <T> T forEachChild(Function<AstNode, T> visitor) {
        return visitor.apply(type);
    }

    @Override
    protected void replaceChild(AstNode oldChild, AstNode newChild) {
        assertReplaced(tryReplace(oldChild, newChild, type, n -> type = n), oldChild);
    }


    @Override
    public long getStartPosition() {
        return keyword.getStartPosition();
    }

    @Override
    public long getEndPosition() {
        return type.getEndPosition();
    }


    // ----- TypeExpression methods ----------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant(Context ctx, ErrorListener errs) {
        return switch (keyword.getId()) {
            case IMMUTABLE ->
                type.ensureTypeConstant(ctx, errs).freeze();

            case CONDITIONAL ->
                // TODO
                throw notImplemented();

            default ->
                throw new IllegalStateException("keyword=" + keyword);
        };
    }

    @Override
    public boolean isIntroductoryType() {
        return true;
    }

    @Override
    public TypeExpression unwrapIntroductoryType() {
        return type;
    }

    @Override
    public void replaceIntroducedType(TypeExpression type) {
        this.type = type;
        type.setParent(this);
    }

    @Override
    protected void collectAnonInnerClassInfo(AnonInnerClass info) {
        type.collectAnonInnerClassInfo(info);
        if (keyword.getId() == Id.IMMUTABLE) {
            info.markImmutable();
        }
    }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        return keyword.getId().TEXT + ' ' + type;
    }

    @Override
    public String getDumpDesc() {
        return toString();
    }

    @Override
    protected AstNode withChildren(List<AstNode> children) {
        return new DecoratedTypeExpression(keyword, (TypeExpression) children.get(0));
    }


    // ----- fields --------------------------------------------------------------------------------

    protected Token          keyword;
    protected TypeExpression type;
}
