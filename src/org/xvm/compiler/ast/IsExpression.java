package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.RelationalTypeConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.IsType;
import org.xvm.asm.op.JumpNType;
import org.xvm.asm.op.JumpType;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Context.Branch;

/**
 * Expression for "expression is expression" or "expression instanceof type".
 */
public class IsExpression
        extends BiExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public IsExpression(Expression expr1, Token operator, TypeExpression expr2)
        {
        super(expr1, operator, expr2);
        }


    // ----- compilation ---------------------------------------------------------------------------

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

        ConstantPool   pool     = pool();
        TypeExpression exprType = (TypeExpression) expr2.validate(ctx, pool.typeType(), errs);
        if (exprType == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            expr2 = exprType;
            }

        Constant constVal = null;
        if (fit.isFit())
            {
            if (exprTarget.isConstant())
                {
                // TODO calculate constant isA -> true or false
                }
            else if (exprTarget instanceof NameExpression)
                {
                NameExpression exprName   = (NameExpression) exprTarget;
                TypeConstant   typeTarget = exprTarget.getType();
                TypeConstant   typeTest   = exprType.ensureTypeConstant();

                ctx.narrowType(exprName.name, Branch.WhenTrue,
                        RelationalTypeConstant.combineWith(pool, typeTarget, typeTest));
                ctx.narrowType(exprName.name, Branch.WhenFalse,
                        RelationalTypeConstant.combineWithout(pool, typeTarget, typeTest));
                }
            }

        return finishValidation(typeRequired, pool.typeBoolean(), fit, constVal, errs);
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
            Argument argType   = ((TypeExpression) expr2).ensureTypeConstant();
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
        Argument argType   = ((TypeExpression) expr2).ensureTypeConstant();
        code.add(fWhenTrue
                ? new JumpType(argTarget, argType, label)
                : new JumpNType(argTarget, argType, label));
        }


    // ----- fields --------------------------------------------------------------------------------

    }
