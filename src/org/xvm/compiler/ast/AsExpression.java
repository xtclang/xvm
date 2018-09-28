package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TerminalTypeConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.MoveCast;

import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Statement.Context;


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
        return rebaseThisClass(ctx, ((TypeExpression) expr2).ensureTypeConstant());
        }

    /**
     * Check if the left expression represents a "this" class constant and update the resulting
     * type accordingly.
     */
    private TypeConstant rebaseThisClass(Context ctx, TypeConstant typeAs)
        {
        if (!typeAs.containsUnresolved() && expr1 instanceof NameExpression
                && ((NameExpression) expr1).getName().equals("this"))
            {
            ConstantPool pool     = pool();
            TypeConstant typeThis = pool.ensureThisTypeConstant(
                                        ctx.getThisClass().getIdentityConstant(), null);

            return typeAs.replaceUnderlying(pool, typeUnder ->
                typeUnder instanceof TerminalTypeConstant ? typeThis : typeUnder);
            }
        return typeAs;
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
            type  = rebaseThisClass(ctx, exprType.ensureTypeConstant());

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
        Register regAfter  = fUsedOnce
                ? new Register(getType(), Op.A_STACK)
                : new Register(getType());
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
