package org.xvm.compiler.ast;


import java.lang.reflect.Field;

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

    /**
     * Copy constructor.
     * <p>
     * Master clone() semantics:
     * <ul>
     *   <li>CHILD_FIELDS: "type" - deep copied by AstNode.clone()</li>
     *   <li>No transient fields in this class</li>
     * </ul>
     *
     * @param original  the DecoratedTypeExpression to copy from
     */
    protected DecoratedTypeExpression(DecoratedTypeExpression original) {
        super(original);

        // Step 1: Copy ALL non-child fields FIRST (matches super.clone() behavior)
        this.keyword = original.keyword;  // Token is immutable

        // Step 2: Deep copy children explicitly
        this.type = original.type == null ? null : original.type.copy();

        // Step 3: Adopt copied children
        if (this.type != null) {
            this.type.setParent(this);
        }
    }

    @Override
    public DecoratedTypeExpression copy() {
        return new DecoratedTypeExpression(this);
    }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    protected boolean canResolveNames() {
        return super.canResolveNames() || type.canResolveNames();
    }

    @Override
    public long getStartPosition() {
        return keyword.getStartPosition();
    }

    @Override
    public long getEndPosition() {
        return type.getEndPosition();
    }

    @Override
    protected Field[] getChildFields() {
        return CHILD_FIELDS;
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


    // ----- fields --------------------------------------------------------------------------------

    protected Token          keyword;
    protected TypeExpression type;

    private static final Field[] CHILD_FIELDS = fieldsForNames(DecoratedTypeExpression.class, "type");
}
