package org.xvm.compiler.ast;

import java.lang.reflect.Field;

import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;

/**
 * An incomplete operation in a value position. Retaining the enclosing assignment or return lets
 * its normal validation establish the real inference and name-resolution context. This node has
 * no implicit type, never fits a requested type, and always fails validation after inspecting the
 * intact prefix. It cannot become a value or reach code emission.
 *
 * The existing partial site is an ordinary AST child, so adoption and cloning preserve its
 * receiver/argument ownership without a second semantic cache or clone-reset protocol.
 */
public final class IncompleteExpression extends Expression {
    public IncompleteExpression(IncompleteStatement site) {
        this.site = site;
    }

    @Override
    public long getStartPosition() {
        return site.getStartPosition();
    }

    @Override
    public long getEndPosition() {
        return site.getEndPosition();
    }

    @Override
    protected Field[] getChildFields() {
        return CHILD_FIELDS;
    }

    @Override
    public TypeConstant getImplicitType(Context ctx) {
        return null;
    }

    @Override
    public TypeConstant[] getImplicitTypes(Context ctx) {
        return TypeConstant.NO_TYPES;
    }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant required, boolean exhaustive, ErrorListener errs) {
        return TypeFit.NoFit;
    }

    @Override
    public TypeFit testFitMulti(Context ctx, TypeConstant[] required, boolean exhaustive, ErrorListener errs) {
        return TypeFit.NoFit;
    }

    @Override
    protected Expression validate(Context ctx, TypeConstant required, ErrorListener errs) {
        site.validate(ctx, errs);
        return null;
    }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] required, ErrorListener errs) {
        site.validate(ctx, errs);
        return null;
    }

    @Override
    public String toString() {
        return site.toString();
    }

    protected IncompleteStatement site;

    private static final Field[] CHILD_FIELDS = fieldsForNames(IncompleteExpression.class, "site");
}
