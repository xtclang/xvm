package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.JumpNotNull;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.Severity;


/**
 * The "Elvis" expression, which is used to optionally substitute the value of the second expression
 * iff the value of the first expression is null.
 *
 * <ul>
 * <li><tt>COND_ELSE:  "?:"</tt> - the "elvis" operator</li>
 * </ul>
 */
public class ElvisExpression
        extends BiExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public ElvisExpression(Expression expr1, Token operator, Expression expr2)
        {
        super(expr1, operator, expr2);
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        TypeConstant type1 = expr1.getImplicitType(ctx);
        TypeConstant type2 = expr2.getImplicitType(ctx);
        if (type1 == null || type2 == null)
            {
            return null;
            }

        // nulls in the first expression are eliminated by using the second expression
        type1 = type1.removeNullable();
        TypeConstant typeResult = selectType(type1, type2, ErrorListener.BLACKHOLE);

        // hey, wouldn't it be nice if we could just do this?
        //
        //   return typeResult ?: pool().ensureIntersectionTypeConstant(type1, type2);
        //
        return typeResult == null
                ? pool().ensureIntersectionTypeConstant(type1, type2)
                : typeResult;
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired)
        {
        TypeFit fit = expr1.testFit(ctx, typeRequired.ensureNullable());
        if (fit.isFit())
            {
            fit.combineWith(expr2.testFit(ctx, typeRequired));
            }
        return fit;
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        TypeFit      fit      = TypeFit.Fit;
        TypeConstant type1Req = typeRequired == null ? null : typeRequired.ensureNullable();
        Expression   expr1New = expr1.validate(ctx, type1Req, errs);
        TypeConstant type1    = null;
        if (expr1New == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            expr1 = expr1New;
            type1 = expr1New.getType();
            }

        TypeConstant type2Req = type1 == null ? null : selectType(type1.removeNullable(), null, errs);
        if (typeRequired != null && (type2Req == null || !expr2.testFit(ctx, type2Req).isFit()))
            {
            type2Req = typeRequired;
            }
        Expression expr2New = expr2.validate(ctx, type2Req, errs);
        if (expr2New == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            expr2 = expr2New;
            }

        if (!fit.isFit())
            {
            return finishValidation(typeRequired, null, fit, null, errs);
            }

        if (type1.isOnlyNullable())
            {
            expr1New.log(errs, Severity.ERROR, Compiler.ELVIS_ONLY_NULLABLE);
            return expr2New;
            }

        ConstantPool pool = pool();
        if (!type1.isNullable() && !pool.typeNull().isA(type1))
            {
            expr1New.log(errs, Severity.ERROR, Compiler.ELVIS_NOT_NULLABLE);
            return expr1New;
            }

        TypeConstant type1Non   = type1.removeNullable();
        TypeConstant type2      = expr2New.getType();
        TypeConstant typeResult = selectType(type1Non, type2, errs);
        if (typeResult == null)
            {
            typeResult = pool.ensureIntersectionTypeConstant(type1Non, type2);
            }

        // in the unlikely event that one or both of the sub expressions are constant, it may be
        // possible to calculate the constant value of this elvis expression
        Constant constVal = null;
        if (expr1New.hasConstantValue())
            {
            Constant const1 = expr1New.toConstant();
            if (const1.equals(pool.valNull()))
                {
                if (expr2New.hasConstantValue())
                    {
                    constVal = expr2New.toConstant();
                    }
                }
            else
                {
                constVal = const1;
                }
            }

        return finishValidation(typeRequired, typeResult, fit, constVal, errs);
        }

    @Override
    public boolean isAborting()
        {
        // these can complete if the first expression can complete, because the result can
        // be calculated from the first expression, depending on what its answer is; thus
        // the expression aborts if the first of the two expressions aborts
        return expr1.isAborting();
        }

    @Override
    public Argument generateArgument(Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        if (hasConstantValue() || getType().isNullable())
            {
            return super.generateArgument(code, fLocalPropOk, fUsedOnce, errs);
            }

        TypeConstant typeTemp = pool().ensureNullableTypeConstant(getType());
        Assignable var = createTempVar(code, typeTemp, false, errs);
        generateAssignment(code, var, errs);
        return var.getRegister();
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        if (hasConstantValue() || !LVal.isNormalVariable() || !LVal.getType().isNullable())
            {
            super.generateAssignment(code, LVal, errs);
            return;
            }

        Label labelEnd = new Label("end?:");

        expr1.generateAssignment(code, LVal, errs);
        code.add(new JumpNotNull(LVal.getRegister(), labelEnd));
        expr2.generateAssignment(code, LVal, errs);
        code.add(labelEnd);
        }


    // ----- fields --------------------------------------------------------------------------------

    }
