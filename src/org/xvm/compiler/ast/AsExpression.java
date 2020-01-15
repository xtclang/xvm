package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Move;
import org.xvm.asm.op.MoveCast;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


/**
 * Expression for "expression as type".
 */
public class AsExpression
        extends BiExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public AsExpression(Expression expr1, Token operator, TypeExpression expr2)
        {
        super(expr1, operator, expr2);
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        return ((TypeExpression) expr2).ensureTypeConstant(ctx);
        }

    @Override
    protected TypeFit calcFit(Context ctx, TypeConstant typeIn, TypeConstant typeOut)
        {
        if (typeIn.containsUnresolved())
            {
            return TypeFit.NoFit;
            }

        return super.calcFit(ctx, typeIn, typeOut);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        ConstantPool   pool        = pool();
        TypeExpression exprType    = (TypeExpression) expr2.validate(ctx, pool.typeType(), errs);
        TypeConstant   type        = null;
        TypeConstant   typeRequest = null;
        boolean        fValid      = true;

        if (exprType == null)
            {
            fValid = false;
            }
        else
            {
            expr2 = exprType;
            if (exprType.isDynamic())
                {
                log(errs, Severity.ERROR, Compiler.UNSUPPORTED_DYNAMIC_TYPE_PARAMS);
                fValid = false;
                }
            else
                {
                type = exprType.ensureTypeConstant(ctx).resolveAutoNarrowingBase(pool);
                }

            if (expr1.testFit(ctx, type, null).isFit())
                {
                typeRequest     = type;
                m_fCastRequired = false;
                }
            }

        Expression exprTarget = expr1.validate(ctx, typeRequest, errs);
        if (exprTarget == null)
            {
            fValid = false;
            }
        else
            {
            expr1 = exprTarget;
            }

        if (fValid)
            {
            Constant constVal = null;
            if (expr1.isConstant())
                {
                constVal = expr1.toConstant();
                }

            return finishValidation(typeRequired, type, TypeFit.Fit, constVal, errs);
            }
        return null;
        }

    @Override
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        Argument     argBefore = expr1.generateArgument(ctx, code, true, true, errs);
        TypeConstant type      = getType();
        Argument     argAfter  = createRegister(type, fUsedOnce);
        if (m_fCastRequired)
            {
            code.add(new MoveCast(argBefore, argAfter, type));
            }
        else
            {
            if (argBefore.getType().equals(type))
                {
                argAfter = argBefore;
                }
            else
                {
                code.add(new Move(argBefore, argAfter));
                }
            }
        return argAfter;
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        if (LVal.isLocalArgument())
            {
            Argument argTarget = expr1.generateArgument(ctx, code, true, true, errs);
            if (m_fCastRequired)
                {
                code.add(new MoveCast(argTarget, LVal.getLocalArgument(), getType()));
                }
            else
                {
                code.add(new Move(argTarget, LVal.getLocalArgument()));
                }
            }
        else
            {
            super.generateAssignment(ctx, code, LVal, errs);
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    private transient boolean m_fCastRequired = true;
    }
