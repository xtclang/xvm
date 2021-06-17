package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.ClassStructure;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.IsType;
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

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        return pool().typeBoolean();
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
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

        Constant constVal = null;
        if (fit.isFit())
            {
            TypeConstant typeTarget = exprTarget.getType();
            TypeConstant typeTest   = exprTest.getType().getParamType(0).resolveAutoNarrowingBase();

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

            if (exprTarget.isConstant() && exprTest.isConstant())
                {
                constVal = pool.valOf(typeTarget.isA(typeTest));
                }
            else if (exprTarget instanceof NameExpression &&
                    ((NameExpression) exprTarget).left == null)
                {
                NameExpression exprName = (NameExpression) exprTarget;

                if (typeTarget.isNestMateOf(ctx.getThisClass().getIdentityConstant()) &&
                        typeTarget.getAccess() != Access.STRUCT)
                    {
                    typeTarget = pool.ensureAccessTypeConstant(typeTarget, Access.PRIVATE);
                    }

                TypeConstant typeInferred = !typeTest.isTuple() &&
                        typeTest.isExplicitClassIdentity(true) &&
                        typeTarget.isExplicitClassIdentity(true)
                            ? computeInferredType(typeTarget, typeTest)
                            : typeTest;

                exprName.narrowType(ctx, Branch.WhenTrue,  typeTarget.combine(pool, typeInferred));
                exprName.narrowType(ctx, Branch.WhenFalse, typeTarget.andNot(pool, typeTest));
                }
            }

        return finishValidation(ctx, typeRequired, pool.typeBoolean(), fit, constVal, errs);
        }

    /**
     * @return an inferred narrowing type based on the original type and "is" type
     */
    private TypeConstant computeInferredType(TypeConstant typeOriginal, TypeConstant typeNarrowing)
        {
        ClassStructure clzOriginal = (ClassStructure) typeOriginal.
                getSingleUnderlyingClass(true).getComponent();

        ClassStructure clzNarrowing = (ClassStructure) typeNarrowing.
                getSingleUnderlyingClass(true).getComponent();

        if (clzOriginal.isParameterized() && clzNarrowing.isParameterized() &&
                clzNarrowing.getCanonicalType().isA(clzOriginal.getCanonicalType()))
            {
            ConstantPool pool = pool();

            TypeConstant typeInferred = clzNarrowing.getFormalType().resolveGenerics(pool,
                sFormalName ->
                    {
                    TypeConstant typeParam = clzNarrowing.getConstraint(sFormalName);
                    int          ixParam   = clzNarrowing.indexOfGenericParameter(sFormalName);

                    // TODO GG: we assume that formal type indexes stay invariant across classes,
                    //          which is not correct in a general case
                    if (typeNarrowing.getParamsCount() > ixParam)
                        {
                        typeParam = typeNarrowing.getParamType(ixParam).combine(pool, typeParam);
                        }
                    else if (typeOriginal.getParamsCount() > ixParam)
                        {
                        typeParam = typeOriginal.getParamType(ixParam).combine(pool, typeParam);
                        }
                    return typeParam;
                    });

            typeInferred = typeNarrowing.adoptParameters(pool, typeInferred.getParamTypesArray());
            if (typeOriginal.isAccessSpecified() && !typeInferred.isAccessSpecified())
                {
                typeInferred = pool.ensureAccessTypeConstant(typeInferred, typeOriginal.getAccess());
                }
            if (typeOriginal.isImmutabilitySpecified() && !typeInferred.isImmutable())
                {
                typeInferred = pool.ensureImmutableTypeConstant(typeInferred);
                }
            return typeInferred;
            }
        return typeNarrowing;
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
                argType = exprTest.generateArgument(ctx, code, false, false, errs);
                }
            code.add(new IsType(argTarget, argType, LVal.getLocalArgument()));
            }
        else
            {
            super.generateAssignment(ctx, code, LVal, errs);
            }
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
