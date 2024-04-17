package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.JumpNotNull;
import org.xvm.asm.op.JumpTrue;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


/**
 * The "Elvis" expression, which is used to optionally substitute the value of the second expression
 * iff the value of the first expression is null.
 *
 * Experimental feature: Alternatively, this expression tests a "conditional" first expression (one
 * that yields both a Boolean and at least one additional value), and substitutes the value of the
 * second expression iff that first Boolean value yielded is False, and otherwise yields the second
 * value from the first expression.
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
        TypeConstant   type1;
        TypeConstant[] atype1 = expr1.getImplicitTypes(ctx);
        switch (atype1.length)
            {
            case 0:
                return null;

            case 1:
                type1 = atype1[0].removeNullable();
                break;

            default:
                TypeConstant type0 = atype1[0];
                type1 = type0.isA(pool().typeBoolean())
                    ? atype1[1]
                    : type0.removeNullable();
                break;
            }

        TypeConstant type2 = expr2.getImplicitType(ctx);
        if (type1 == null || type2 == null)
            {
            return null;
            }

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
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, boolean fExhaustive, ErrorListener errs)
        {
        // first try the less likely (and more complicated) "conditional" use case
        ErrorListener  errsTemp  = errs == null ? ErrorListener.BLACKHOLE : errs.branch(this);
        TypeConstant[] atypeCond = new TypeConstant[]{pool().typeBoolean(), typeRequired};
        TypeFit        fit       = expr1.testFitMulti(ctx, atypeCond, fExhaustive, errsTemp);
        if (fit.isFit())
            {
            errsTemp.merge();
            }
        else
            {
            fit = expr1.testFit(ctx, typeRequired.ensureNullable(), fExhaustive, errs);
            }

        if (fit.isFit())
            {
            fit = fit.combineWith(expr2.testFit(ctx, typeRequired, fExhaustive, errs));
            }

        return fit;
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        // we need to make a quick decision: either the first expression is a "T?" and is tested
        // for non-null, or the first expression is a "conditional T" / "(Boolean, T)" and the
        // Boolean is tested for True
        ConstantPool   pool      = pool();
        TypeFit        fit       = TypeFit.Fit;
        boolean        fCond     = false;
        TypeConstant[] atypeCond = new TypeConstant[]{pool.typeBoolean(), pool.typeObject()};
        TypeConstant   type1     = null;
        Expression     expr1New;

        // ElvisExpression is structurally equivalent to one of the following expression statements:
        //      {
        //      if (T result_ ?= [expr1]) {}
        //      else {result_ = [expr2];}
        //      return result_;
        //      }
        // or
        //      {
        //      if (T result_ := [expr1]) {}
        //      else {result_ = [expr2];}
        //      return result_;
        //      }
        //
        ctx = ctx.enterIf();

        if (expr1.testFitMulti(ctx, atypeCond, true, ErrorListener.BLACKHOLE).isFit())
            {
            m_fCond = fCond = true;

            if (typeRequired != null)
                {
                atypeCond[1] = typeRequired;
                }
            expr1New = expr1.validateMulti(ctx, atypeCond, errs);
            }
        else
            {
            TypeConstant type1Req = typeRequired == null ? null : typeRequired.ensureNullable();
            expr1New = expr1.validate(ctx, type1Req, errs);
            }

        if (expr1New == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            expr1 = expr1New;
            type1 = fCond ? expr1New.getTypes()[1] : expr1New.getType();
            }

        TypeConstant type2Req = type1 == null
                ? null
                : Op.selectCommonType(type1.removeNullable(), null, errs);
        if (typeRequired != null &&
                (type2Req == null || !expr2.testFit(ctx, type2Req, false, null).isFit()))
            {
            type2Req = typeRequired;
            }

        ctx = ctx.enterFork(true);
        ctx = ctx.exit();
        ctx = ctx.enterFork(false);

        Expression expr2New = expr2.validate(ctx, type2Req, errs);
        if (expr2New == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            expr2 = expr2New;
            }
        ctx = ctx.exit(); // else
        ctx = ctx.exit(); // if

        if (!fit.isFit())
            {
            return finishValidation(ctx, typeRequired, null, fit, null, errs);
            }

        TypeConstant type1Non;
        TypeConstant type2 = expr2New.getType();
        if (fCond)
            {
            type1Non = type1;
            }
        else
            {
            if (type1.isOnlyNullable())
                {
                expr1New.log(errs, Severity.ERROR, Compiler.ELVIS_ONLY_NULLABLE);
                return null;
                }

            // the second check is for not-nullable type that is still allowed to be assigned from
            // null (e.g. Object or Const)
            if (!type1.isNullable() && !pool.typeNull().isA(type1.resolveConstraints()))
                {
                expr1New.log(errs, Severity.ERROR, Compiler.ELVIS_NOT_NULLABLE);
                return null;
                }

            type1Non = type1.removeNullable();
            }

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
            if (fCond)
                {
                Constant[] aconst1 = expr1New.toConstants();
                if (aconst1[0] == pool().valTrue())
                    {
                    constVal = aconst1[1];
                    }
                else if (aconst1[0] == pool().valFalse())
                    {
                    if (expr2New.isConstant())
                        {
                        constVal = expr2New.toConstant();
                        }
                    }
                }
            else
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
            }

        return finishValidation(ctx, typeRequired, typeResult, fit, constVal, errs);
        }

    @Override
    protected boolean allowsConditional(Expression exprChild)
        {
        return m_fCond && exprChild == expr1;
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

        if (m_fCond)
            {
            Label        labelEnd = getEndLabel();
            Assignable   varCond  = createTempVar(code, pool().typeBoolean(), true);
            Assignable   varVal   = createTempVar(code, getType(), false);
            Assignable[] LVals    = new Assignable[] {varCond, varVal};
            expr1.generateAssignments(ctx, code, LVals, errs);
            code.add(new JumpTrue(varCond.getRegister(), labelEnd));
            expr2.generateAssignment(ctx, code, varVal, errs);
            code.add(labelEnd);
            return varVal.getRegister();
            }
        else
            {
            TypeConstant typeTemp = getType().ensureNullable();
            Assignable var = createTempVar(code, typeTemp, false);
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
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        if (isConstant() || !LVal.isNormalVariable() || !m_fCond && !pool().typeNull().isA(LVal.getType()))
            {
            super.generateAssignment(ctx, code, LVal, errs);
            return;
            }

        Label labelEnd = getEndLabel();
        if (m_fCond)
            {
            Assignable   varCond = createTempVar(code, pool().typeBoolean(), true);
            Assignable[] LVals   = new Assignable[] {varCond, LVal};
            expr1.generateAssignments(ctx, code, LVals, errs);
            code.add(new JumpTrue(varCond.getRegister(), labelEnd));
            }
        else
            {
            expr1.generateAssignment(ctx, code, LVal, errs);
            code.add(new JumpNotNull(LVal.getLocalArgument(), labelEnd));
            }
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

    private static int s_nCounter;

    /**
     * True iff the short-circuit operator is used to convert a "(Boolean, T)" into a "T".
     */
    private transient boolean m_fCond;

    private transient Label m_labelEnd;
    }