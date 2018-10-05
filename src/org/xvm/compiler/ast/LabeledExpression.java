package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Label;

import org.xvm.compiler.Token;

import java.lang.reflect.Field;


/**
 * Used for named arguments.
 */
public class LabeledExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public LabeledExpression(Token name, Expression expr)
        {
        this.name = name;
        this.expr = expr;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the token that provides the label (the name) for the expression
     */
    public Token getNameToken()
        {
        return name;
        }

    /**
     * @return the label name
     */
    public String getName()
        {
        return name.getValueText();
        }

    @Override
    public long getStartPosition()
        {
        return name.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return expr.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected boolean hasMultiValueImpl()
        {
        return true;
        }

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        return expr.getImplicitType(ctx);
        }

    @Override
    public TypeConstant[] getImplicitTypes(Context ctx)
        {
        return expr.getImplicitTypes(ctx);
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired)
        {
        return expr.testFit(ctx, typeRequired);
        }

    @Override
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired)
        {
        return expr.testFitMulti(ctx, atypeRequired);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        Expression exprNew = expr.validate(ctx, typeRequired, errs);
        if (exprNew == null)
            {
            return finishValidation(typeRequired, null, TypeFit.NoFit, null, errs);
            }

        expr = exprNew;
        return finishValidation(typeRequired, exprNew.getType(), exprNew.getTypeFit(),
                exprNew.isConstant() ? exprNew.toConstant() : null, errs);
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        Expression exprNew = expr.validateMulti(ctx, atypeRequired, errs);
        if (exprNew == null)
            {
            return finishValidations(atypeRequired, null, TypeFit.NoFit, null, errs);
            }

        expr = exprNew;
        return finishValidations(atypeRequired, exprNew.getTypes(), exprNew.getTypeFit(),
                exprNew.isConstant() ? exprNew.toConstants() : null, errs);
        }

    @Override
    public boolean isAssignable()
        {
        return expr.isAssignable();
        }

    @Override
    public void requireAssignable(Context ctx, ErrorListener errs)
        {
        expr.requireAssignable(ctx, errs);
        }

    @Override
    public void markAssignment(Context ctx, boolean fCond, ErrorListener errs)
        {
        expr.markAssignment(ctx, fCond, errs);
        }

    @Override
    public boolean isCompletable()
        {
        return expr.isCompletable();
        }

    @Override
    public boolean isShortCircuiting()
        {
        return expr.isShortCircuiting();
        }

    @Override
    public boolean isNonBinding()
        {
        return expr.isNonBinding();
        }

    @Override
    public boolean isRuntimeConstant()
        {
        return expr.isRuntimeConstant();
        }

    @Override
    public boolean hasSideEffects()
        {
        return expr.hasSideEffects();
        }

    @Override
    public void generateVoid(Context ctx, Code code, ErrorListener errs)
        {
        expr.generateVoid(ctx, code, errs);
        }

    @Override
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        return expr.generateArgument(ctx, code, fLocalPropOk, fUsedOnce, errs);
        }

    @Override
    public Argument[] generateArguments(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        return expr.generateArguments(ctx, code, fLocalPropOk, fUsedOnce, errs);
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        expr.generateAssignment(ctx, code, LVal, errs);
        }

    @Override
    public void generateAssignments(Context ctx, Code code, Assignable[] aLVal, ErrorListener errs)
        {
        expr.generateAssignments(ctx, code, aLVal, errs);
        }

    @Override
    public void generateConditionalJump(
            Context ctx, Code code, Label label, boolean fWhenTrue, ErrorListener errs)
        {
        expr.generateConditionalJump(ctx, code, label, fWhenTrue, errs);
        }

    @Override
    public Assignable generateAssignable(Context ctx, Code code, ErrorListener errs)
        {
        return expr.generateAssignable(ctx, code, errs);
        }

    @Override
    public Assignable[] generateAssignables(Context ctx, Code code, ErrorListener errs)
        {
        return expr.generateAssignables(ctx, code, errs);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return name + " = " + expr;
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token      name;
    protected Expression expr;

    private static final Field[] CHILD_FIELDS = fieldsForNames(LabeledExpression.class, "expr");
    }
