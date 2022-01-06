package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.IsType;
import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.JumpNType;
import org.xvm.asm.op.JumpType;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Context.Branch;

import org.xvm.util.Severity;


/**
 * Expression for "expression is expression" or "expression instanceof type".
 */
public class IsExpression
        extends BiExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public IsExpression(Expression expr1, Token operator, Expression expr2, Token tokClose)
        {
        super(expr1, operator, expr2);

        lEndPos = tokClose.getEndPosition();
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public long getEndPosition()
        {
        return lEndPos;
        }

    /**
     * @return true iff the expression implements the "n type" / "n value" code path
     */
    protected boolean hasMultiValueImpl()
        {
        return true;
        }

    @Override
    public TypeConstant[] getImplicitTypes(Context ctx)
        {
        return new TypeConstant[] {pool().typeBoolean(), expr2.getImplicitType(ctx)};
        }

    @Override
    public boolean isConditionalResult()
        {
        return true;
        }

    @Override
    protected Expression validateMulti(Context ctx, TypeConstant[] atypeRequired, ErrorListener errs)
        {
        TypeFit fit = TypeFit.Fit;

        Expression exprTarget = expr1.validate(ctx, null, errs);
        if (exprTarget == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            expr1 = exprTarget;
            }

        ConstantPool pool     = pool();
        Expression   exprTest = expr2.validate(ctx, pool.typeType(), errs);
        if (exprTest == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            expr2 = exprTest;
            }

        boolean        fSingle     = atypeRequired.length <= 1;
        TypeConstant[] atypeActual = new TypeConstant[fSingle ? 1 : 2];
        Constant[]     aconstVal   = null;

        atypeActual[0] = pool.typeBoolean();

        if (fit.isFit())
            {
            TypeConstant typeTarget   = exprTarget.getType();
            TypeConstant typeTest     = exprTest.getType().getParamType(0).resolveAutoNarrowingBase();
            TypeConstant typeInferred = typeTest;

            if (typeTarget.isTypeOfType() && !typeTest.isFormalType() && exprTest.isConstant())
                {
                // the test must be a type unless it's something that any Type is (e.g. Const),
                // in which case just issue a warning
                if (!typeTest.isTypeOfType())
                    {
                    if (pool.typeConst().isA(typeTest))
                        {
                        log(errs, Severity.WARNING, Compiler.TYPE_MATCHES_ALWAYS,
                            exprTarget.toString(), typeTest.getValueString());
                        }
                    else
                        {
                        log(errs, Severity.ERROR, Compiler.NOT_TYPE_OF_TYPE,
                            exprTarget.toString(), typeTest.getValueString());
                        return null;
                        }
                    }
                }

            if (exprTarget.isConstant() && exprTest.isConstant() && fSingle)
                {
                aconstVal = new Constant[] {pool.valOf(typeTarget.isA(typeTest))};
                }
            else if (exprTarget instanceof NameExpression)
                {
                NameExpression exprName = (NameExpression) exprTarget;

                if (typeTarget.isNestMateOf(ctx.getThisClass().getIdentityConstant()) &&
                        typeTarget.getAccess() != Access.STRUCT)
                    {
                    typeTarget = pool.ensureAccessTypeConstant(typeTarget, Access.PRIVATE);
                    }

                typeInferred = !typeTest.isTuple() &&
                        typeTest.isExplicitClassIdentity(true) &&
                        typeTarget.isExplicitClassIdentity(true)
                            ? computeInferredType(typeTarget, typeTest)
                            : typeTest;

                if (typeInferred == typeTest)
                    {
                    typeInferred = typeTarget.combine(pool, typeTest);
                    }

                exprName.narrowType(ctx, Branch.WhenTrue,  typeInferred);
                exprName.narrowType(ctx, Branch.WhenFalse, typeTarget.andNot(pool, typeTest));
                }

            if (!fSingle)
                {
                atypeActual[1] = typeInferred;
                }
            }
        else if (!fSingle)
            {
            // there's an error already, so just pretend that we return what was requested for the
            // conditional type
            atypeActual[1] = atypeRequired[1];
            }

        return finishValidations(ctx, atypeRequired, atypeActual, fit, aconstVal, errs);
        }

    /**
     * @return an inferred narrowing type based on the original type and "is" type
     */
    private TypeConstant computeInferredType(TypeConstant typeOriginal, TypeConstant typeNarrowing)
        {
        ConstantPool   pool         = pool();
        ClassStructure clzNarrowing = (ClassStructure) typeNarrowing.
                getSingleUnderlyingClass(true).getComponent();
        ClassStructure clzOriginal  = (ClassStructure) typeOriginal.
                getSingleUnderlyingClass(true).getComponent();
        TypeConstant   typeInferred  = typeNarrowing;

        if (clzNarrowing.getFormat() == Component.Format.MIXIN &&
            clzOriginal.getFormat()  != Component.Format.MIXIN &&
                typeOriginal.isA(typeNarrowing.getExplicitClassInto(true)))
            {
            typeInferred = pool.ensureAnnotatedTypeConstant(clzNarrowing.getIdentityConstant(),
                                null, typeOriginal.removeImmutable().removeAccess());
            }
        else if (clzOriginal.isParameterized() && clzNarrowing.isParameterized() &&
                clzNarrowing.getCanonicalType().isA(clzOriginal.getCanonicalType()))
            {
            typeInferred = clzNarrowing.getFormalType().resolveGenerics(pool,
                sFormalName ->
                    {
                    TypeConstant typeParam = clzNarrowing.getConstraint(sFormalName);
                    int          ixParam   = clzNarrowing.indexOfGenericParameter(sFormalName);

                    // TODO GG: we assume that formal type indexes stay invariant across classes,
                    //          which is not correct in a general case
                    if (typeOriginal.getParamsCount() > ixParam)
                        {
                        typeParam = typeOriginal.getParamType(ixParam).combine(pool, typeParam);
                        }
                    if (typeNarrowing.getParamsCount() > ixParam)
                        {
                        typeParam = typeNarrowing.getParamType(ixParam).combine(pool, typeParam);
                        }
                    return typeParam;
                    });
            typeInferred = typeNarrowing.adoptParameters(pool, typeInferred.getParamTypesArray());
            }

        if (typeInferred != typeNarrowing)
            {
            if (typeOriginal.isAccessSpecified() && !typeInferred.isAccessSpecified())
                {
                typeInferred = pool.ensureAccessTypeConstant(typeInferred, typeOriginal.getAccess());
                }
            if (typeOriginal.isImmutabilitySpecified() && !typeInferred.isImmutable())
                {
                typeInferred = pool.ensureImmutableTypeConstant(typeInferred);
                }
            }
        return typeInferred;
        }

    @Override
    public boolean isRuntimeConstant()
        {
        return expr1.isRuntimeConstant();
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        if (LVal.isLocalArgument())
            {
            Argument argTarget = expr1.generateArgument(ctx, code, true, true, errs);
            Argument argType;

            Expression exprTest = expr2;
            if (exprTest.isConstant())
                {
                argType = exprTest.getType().getParamType(0).resolveAutoNarrowingBase();
                }
            else
                {
                argType = exprTest.generateArgument(ctx, code, false, true, errs);
                }
            code.add(new IsType(argTarget, argType, LVal.getLocalArgument()));
            }
        else
            {
            super.generateAssignment(ctx, code, LVal, errs);
            }
        }

    @Override
    public Argument[] generateArguments(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        if (getValueCount() != 2)
            {
            return super.generateArguments(ctx, code, fLocalPropOk, fUsedOnce, errs);
            }

        Argument     argTarget = expr1.generateArgument(ctx, code, false, false, errs);
        Expression   exprTest  = expr2;
        TypeConstant typeTest;
        Argument     argType;
        if (exprTest.isConstant())
            {
            argType = typeTest = exprTest.getType().getParamType(0).resolveAutoNarrowingBase();
            }
        else
            {
            typeTest = getTypes()[1];
            argType  = exprTest.generateArgument(ctx, code, false, true, errs);
            }

        Assignable varBool = createTempVar(code, pool().typeBoolean(), fUsedOnce);
        Argument   argBool = varBool.getRegister();
        code.add(new IsType(argTarget, argType, argBool));
        if (fUsedOnce)
            {
            return new Argument[]{argBool, argTarget};
            }

        Assignable varObj  = createTempVar(code, typeTest, fUsedOnce);
        Label      label   = new Label("skip_copy");
        code.add(new JumpFalse(argBool, label));
        varObj.assign(argTarget, code, errs);
        code.add(label);
        return new Argument[]{argBool, varObj.getRegister()};
        }

    @Override
    public void generateConditionalJump(
            Context ctx, Code code, Label label, boolean fWhenTrue, ErrorListener errs)
        {
        Argument argTarget = expr1.generateArgument(ctx, code, true, true, errs);
        Argument argType   = expr2.getType().getParamType(0).resolveAutoNarrowingBase();
        code.add(fWhenTrue
                ? new JumpType(argTarget, argType, label)
                : new JumpNType(argTarget, argType, label));
        }


    // ----- fields --------------------------------------------------------------------------------

    protected long lEndPos;
    }
