package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.CastTypeConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Move;
import org.xvm.asm.op.MoveCast;

import org.xvm.compiler.Token;


/**
 * Expression for "expression as type".
 */
public class AsExpression
        extends BiExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public AsExpression(Expression expr1, Token operator, TypeExpression expr2, Token tokClose)
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
        return ((TypeExpression) expr2).ensureTypeConstant(ctx, null);
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
        TypeExpression exprType    = (TypeExpression) expr2.validate(ctx, pool().typeType(), errs);
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
            type  = exprType.ensureTypeConstant(ctx, errs).resolveAutoNarrowingBase();

            if (!exprType.isDynamic() && expr1.testFit(ctx, type, null).isFit())
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

            TypeConstant typeTarget = exprTarget.getType();
            if (!type.isA(typeTarget))
                {
                type = new CastTypeConstant(pool(), typeTarget, type);
                }
            }

        if (fValid)
            {
            Constant constVal = null;
            if (!m_fCastRequired && expr1.isConstant())
                {
                constVal = expr1.toConstant();
                }

            return finishValidation(ctx, typeRequired, type, TypeFit.Fit, constVal, errs);
            }
        return null;
        }

    @Override
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        Argument     argBefore = expr1.generateArgument(ctx, code, true, true, errs);
        TypeConstant type      = getTargetType();

        if (m_fCastRequired || !argBefore.getType().equals(type))
            {
            Argument argAfter = createRegister(type, fUsedOnce);

            code.add(new MoveCast(argBefore, argAfter, type));
            return argAfter;
            }
        else
            {
            return argBefore;
            }
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        if (LVal.isLocalArgument())
            {
            Argument argTarget = expr1.generateArgument(ctx, code, true, true, errs);
            if (m_fCastRequired)
                {
                code.add(new MoveCast(argTarget, LVal.getLocalArgument(), getTargetType()));
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

    private TypeConstant getTargetType()
        {
        TypeConstant typeTarget = getType();
        return typeTarget instanceof CastTypeConstant
                ? typeTarget.getUnderlyingType2()
                : typeTarget;
        }


    // ----- fields --------------------------------------------------------------------------------

    protected long lEndPos;

    private transient boolean m_fCastRequired = true;
    }
