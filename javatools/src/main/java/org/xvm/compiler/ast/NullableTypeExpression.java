package org.xvm.compiler.ast;


import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;

import org.xvm.util.Severity;


/**
 * A nullable type expression is a type expression followed by a question mark.
 */
public class NullableTypeExpression
        extends UnaryTypeExpression {
    // ----- constructors --------------------------------------------------------------------------

    public NullableTypeExpression(TypeExpression type, long lEndPos) {
        super(type);

        this.lEndPos = lEndPos;
    }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition() {
        return type.getStartPosition();
    }

    @Override
    public long getEndPosition() {
        return lEndPos;
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

    protected long lEndPos;
}
