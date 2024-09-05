package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.ast.ExprAST;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Label;


/**
 * Delegates to an underlying expression.
 */
public abstract class DelegatingExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    protected DelegatingExpression(Expression expr)
        {
        this.expr = expr;
        }

    /**
     * @return the underlying expression
     */
    public Expression getUnderlyingExpression()
        {
        return expr;
        }

    @Override
    public void markConditional()
        {
        expr.markConditional();
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
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, boolean fExhaustive,
                           ErrorListener errs)
        {
        return expr.testFit(ctx, typeRequired, fExhaustive, errs);
        }

    @Override
    public TypeFit testFitMulti(Context ctx, TypeConstant[] atypeRequired, boolean fExhaustive,
                                ErrorListener errs)
        {
        return expr.testFitMulti(ctx, atypeRequired, fExhaustive, errs);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        Expression exprNew = expr.validate(ctx, typeRequired, errs);
        if (exprNew == null)
            {
            return finishValidation(ctx, typeRequired, null, TypeFit.NoFit, null, errs);
            }

        expr = exprNew;
        return finishValidation(ctx, typeRequired, exprNew.getType(), exprNew.getTypeFit(),
                exprNew.isConstant() ? exprNew.toConstant() : null, errs);
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        Expression exprNew = expr.validateMulti(ctx, atypeRequired, errs);
        if (exprNew == null)
            {
            return finishValidations(ctx, atypeRequired, null, TypeFit.NoFit, null, errs);
            }

        expr = exprNew;
        return finishValidations(ctx, atypeRequired, exprNew.getTypes(), exprNew.getTypeFit(),
                exprNew.isConstant() ? exprNew.toConstants() : null, errs);
        }

    @Override
    public boolean isAssignable(Context ctx)
        {
        return expr.isAssignable(ctx);
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

    @Override
    public ExprAST getExprAST(Context ctx)
        {
        return expr.getExprAST(ctx);
        }

    @Override
    protected SideEffect mightAffect(Expression exprLeft, Argument arg)
        {
        return expr.mightAffect(exprLeft, arg);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression expr;
    }