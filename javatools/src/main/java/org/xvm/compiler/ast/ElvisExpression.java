package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.JumpNotNull;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

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

        TypeConstant typeResult = Op.selectCommonType(type1, type2, ErrorListener.BLACKHOLE);

        // hey, wouldn't it be nice if we could just do this?
        //
        //   return typeResult ?: pool().ensureUnionTypeConstant(type1, type2);
        //
        return typeResult == null
                ? pool().ensureUnionTypeConstant(type1, type2)
                : typeResult;
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        TypeFit fit = expr1.testFit(ctx, typeRequired.ensureNullable(), errs);
        if (fit.isFit())
            {
            fit = fit.combineWith(expr2.testFit(ctx, typeRequired, errs));
            }
        return fit;
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        ConstantPool pool     = pool();
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

        TypeConstant type2Req = type1 == null ? null :
                Op.selectCommonType(type1.removeNullable(), null, errs);

        if (typeRequired != null && (type2Req == null || !expr2.testFit(ctx, type2Req, null).isFit()))
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
            return finishValidation(ctx, typeRequired, null, fit, null, errs);
            }

        if (type1.isOnlyNullable())
            {
            expr1New.log(errs, Severity.ERROR, Compiler.ELVIS_ONLY_NULLABLE);
            return replaceThisWith(expr2New);
            }

        // the second check is for not-nullable type that is still allowed to be assigned from null
        // (e.g. Object or Const)
        if (!type1.isNullable() && !pool.typeNull().isA(type1.resolveConstraints()))
            {
            expr1New.log(errs, Severity.ERROR, Compiler.ELVIS_NOT_NULLABLE);
            return replaceThisWith(expr1New);
            }

        TypeConstant type1Non   = type1.removeNullable();
        TypeConstant type2      = expr2New.getType();
        TypeConstant typeResult = Op.selectCommonType(type1Non, type2, errs);
        if (typeResult == null)
            {
            typeResult = pool.ensureUnionTypeConstant(type1Non, type2);
            }

        // in the unlikely event that one or both of the sub expressions are constant, it may be
        // possible to calculate the constant value of this elvis expression
        Constant constVal = null;
        if (expr1New.isConstant())
            {
            Constant const1 = expr1New.toConstant();
            if (const1.equals(pool.valNull()))
                {
                if (expr2New.isConstant())
                    {
                    constVal = expr2New.toConstant();
                    }
                }
            else
                {
                constVal = const1;
                }
            }

        return finishValidation(ctx, typeRequired, typeResult, fit, constVal, errs);
        }

    @Override
    public boolean isCompletable()
        {
        // these can complete if the first expression can complete, because the result can
        // be calculated from the first expression, depending on what its answer is
        return expr1.isCompletable();
        }

    @Override
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        if (isConstant())
            {
            return super.generateArgument(ctx, code, fLocalPropOk, fUsedOnce, errs);
            }

        TypeConstant typeTemp = getType().ensureNullable();
        Assignable   var      = createTempVar(code, typeTemp, false);
        generateAssignment(ctx, code, var, errs);

        /*  Alternatively, and particularly if there were a way to ask expr1 if it can provide us an
            argument at no cost, we could do something like:

            Label labelEnd  = getEndLabel();
            Label labelElse = new Label("else_?:_" + (++s_nCounter));

            Argument arg1 = expr1.generateArgument(ctx, code, false, false, errs);
            code.add(new JumpNull(arg1, labelElse));
            var.assign(arg1, code, errs);
            code.add(new Jump(labelEnd));

            code.add(labelElse);
            Argument arg2 = expr2.generateArgument(ctx, code, false, true, errs);
            var.assign(arg2, code, errs);
            code.add(labelEnd);
        */

        return var.getRegister();
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        if (isConstant() || !LVal.isNormalVariable() || !pool().typeNull().isA(LVal.getType()))
            {
            super.generateAssignment(ctx, code, LVal, errs);
            return;
            }

        Label labelEnd = getEndLabel();
        expr1.generateAssignment(ctx, code, LVal, errs);
        code.add(new JumpNotNull(LVal.getLocalArgument(), labelEnd));
        expr2.generateAssignment(ctx, code, LVal, errs);
        code.add(labelEnd);
        }

    protected Label getEndLabel()
        {
        Label labelEnd = m_labelEnd;
        if (labelEnd == null)
            {
            m_labelEnd = labelEnd = new Label("end_?:_" + (++s_nCounter));
            }
        return labelEnd;
        }

    // ----- fields --------------------------------------------------------------------------------

    private static    int s_nCounter;
    private transient Label m_labelEnd;
    }
