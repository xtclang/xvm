package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;

import org.xvm.util.Severity;


/**
 * A nullable type expression is a type expression followed by a question mark.
 */
public class NullableTypeExpression
        extends TypeExpression {
    // ----- constructors --------------------------------------------------------------------------

    public NullableTypeExpression(TypeExpression type, long lEndPos) {
        this.type    = type;
        this.lEndPos = lEndPos;
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
     * @param original  the NullableTypeExpression to copy from
     */
    protected NullableTypeExpression(NullableTypeExpression original) {
        super(original);

        // Step 1: Copy ALL non-child fields FIRST (matches super.clone() behavior)
        this.lEndPos = original.lEndPos;

        // Step 2: Deep copy children explicitly
        this.type = original.type == null ? null : original.type.copy();

        // Step 3: Adopt copied children
        if (this.type != null) {
            this.type.setParent(this);
        }
    }

    @Override
    public NullableTypeExpression copy() {
        return new NullableTypeExpression(this);
    }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    protected boolean canResolveNames() {
        return super.canResolveNames() || type.canResolveNames();
    }

    @Override
    public long getStartPosition() {
        return type.getStartPosition();
    }

    @Override
    public long getEndPosition() {
        return lEndPos;
    }

    @Override
    protected Field[] getChildFields() {
        return CHILD_FIELDS;
    }


    // ----- TypeConstant methods ------------------------------------------------------------------

    @Override
    protected TypeConstant instantiateTypeConstant(Context ctx, ErrorListener errs) {
        ConstantPool pool = pool();
        return pool.ensureUnionTypeConstant(
                pool.typeNullable(), type.ensureTypeConstant(ctx, errs));
    }

    @Override
    protected void collectAnonInnerClassInfo(AnonInnerClass info) {
        log(info.getErrorListener(true), Severity.ERROR, Compiler.ANON_CLASS_EXTENDS_UNION);
    }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs) {
        TypeExpression exprOld = type;
        TypeExpression exprNew = (TypeExpression) exprOld.validate(ctx, typeRequired, errs);
        if (exprNew == null) {
            return null;
        }
        type = exprNew;

        TypeConstant typeActual = pool().ensureNullableTypeConstant(exprNew.ensureTypeConstant(ctx, errs));
        TypeConstant typeType   = typeActual.getType();

        return finishValidation(ctx, typeRequired, typeType, TypeFit.Fit, typeType, errs);
    }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString() {
        return type + "?";
    }

    @Override
    public String getDumpDesc() {
        return toString();
    }


    // ----- fields --------------------------------------------------------------------------------

    protected TypeExpression type;
    protected long           lEndPos;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NullableTypeExpression.class, "type");
}
