package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.MoveCast;

import org.xvm.compiler.Token;


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
        return ((TypeExpression) expr2).ensureTypeConstant();
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
        TypeFit fit = TypeFit.Fit;

        ConstantPool   pool        = pool();
        TypeExpression exprType    = (TypeExpression) expr2.validate(ctx, pool.typeType(), errs);
        TypeConstant   type        = null;
        TypeConstant   typeRequest = null;
        if (exprType == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            expr2 = exprType;
            type  = exprType.ensureTypeConstant();

            // it would be nice if the expression could provide us the type without any additional
            // work!
            if (expr1.testFit(ctx, type).isFit())
                {
                typeRequest = type;
                }
            }

        Expression exprTarget = expr1.validate(ctx, typeRequest, errs);
        if (exprTarget == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            expr1 = exprTarget;
            }

        Constant constVal = null;
        if (expr1.isConstant())
            {
            // TODO calculate constant if possible
            }

        return finishValidation(typeRequired, type, fit, constVal, errs);
        }

    @Override
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        Argument argBefore = expr1.generateArgument(ctx, code, true, true, errs);
        Register regAfter  = createRegister(getType(), fUsedOnce);
        code.add(new MoveCast(argBefore, regAfter));
        return code.lastRegister();
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        if (LVal.isNormalVariable())
            {
            Argument argTarget = expr1.generateArgument(ctx, code, true, true, errs);
            code.add(new MoveCast(argTarget, LVal.getRegister()));
            }
        else
            {
            super.generateAssignment(ctx, code, LVal, errs);
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    }
