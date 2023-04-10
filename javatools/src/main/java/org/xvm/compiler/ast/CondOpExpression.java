package org.xvm.compiler.ast;


import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.JumpTrue;
import org.xvm.asm.op.Label;
import org.xvm.asm.op.Var;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.util.Handy;


/**
 * Conditional operator expressions "||" and "&&".
 *
 * <ul>
 * <li><tt>COND_OR:  "||"</tt> - logical "or"</li>
 * <li><tt>COND_AND: "&&"</tt> - logical "and"</li>
 * </ul>
 */
public class CondOpExpression
        extends BiExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public CondOpExpression(Expression expr1, Token operator, Expression expr2)
        {
        super(expr1, operator, expr2);

        switch (operator.getId())
            {
            case COND_OR:
            case COND_AND:
                break;

            default:
                throw new IllegalArgumentException("operator: " + operator);
            }
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff the expression is a conditional "and" expression
     */
    public boolean isAnd()
        {
        return operator.getId() == Id.COND_AND;
        }

    /**
     * @return true iff the expression is a conditional "or" expression
     */
    public boolean isOr()
        {
        return operator.getId() == Id.COND_OR;
        }

    @Override
    public boolean validateCondition(ErrorListener errs)
        {
        return expr1.validateCondition(errs) && expr2.validateCondition(errs);
        }

    @Override
    public ConditionalConstant toConditionalConstant()
        {
        return isAnd()
                ? expr1.toConditionalConstant().addAnd(expr2.toConditionalConstant())
                : expr1.toConditionalConstant().addOr (expr2.toConditionalConstant());
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

        ConstantPool pool        = pool();
        TypeConstant typeBoolean = pool.typeBoolean();
        Expression   expr1Old    = expr1;
        Expression   expr1New    = expr1Old.validate(ctx, typeBoolean, errs);
        Constant     const1      = null;
        if (expr1New == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            if (expr1New != expr1Old)
                {
                expr1 = expr1New;
                }

            const1 = expr1New.toConstant();
            }

        // && -> the second expression is only evaluated if the first expression is true
        // || -> the second expression is only evaluated if the first expression is false
        ctx = isAnd() ? ctx.enterAnd() : ctx.enterOr();
        Expression expr2Old = expr2;
        Expression expr2New = expr2Old.validate(ctx, typeBoolean, errs);
        Constant   const2   = null;
        ctx = ctx.exit();
        if (expr2New == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            if (expr2New != expr2Old)
                {
                expr2 = expr2New;
                }

            const2 = expr2New.toConstant();
            }

        Constant constResult = null;
        switch (combine(const1, getOperatorString(), const2))
            {
            case UandF:
                if (expr1.hasSideEffects())
                    {
                    break;
                    }
                // fall through
            case ForF:
            case FandF:
            case FandT:
            case FandU:
            case TandF:
                constResult = pool.valFalse();
                break;

            case UorT:
                if (expr1.hasSideEffects())
                    {
                    break;
                    }
                // fall through
            case ForT:
            case TorF:
            case TorT:
            case TorU:
            case TandT:
                constResult = pool.valTrue();
                break;
            }

        return finishValidation(ctx, typeRequired, typeBoolean, fit, constResult, errs);
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
            return toConstant();
            }

        switch (combine(expr1.toConstant(), getOperatorString(), expr1.toConstant()))
            {
            case UorF:
            case UandT:
                // result is the same as the result of the first expression
                return expr1.generateArgument(ctx, code, fLocalPropOk, fUsedOnce, errs);

            case ForU:
            case TandU:
                // result is the same as the result of the second expression
                return expr2.generateArgument(ctx, code, fLocalPropOk, fUsedOnce, errs);

            case UorU:
            case UandU:
                Label      labelEnd  = new Label();
                Register   regAccum  = code.createRegister(getType());
                Assignable LValAccum = new Assignable(regAccum);

                code.add(new Var(regAccum));
                expr1.generateAssignment(ctx, code, LValAccum, errs);
                if (isAnd())
                    {
                    code.add(new JumpFalse(regAccum, labelEnd));
                    }
                else
                    {
                    code.add(new JumpTrue(regAccum, labelEnd));
                    }
                expr2.generateAssignment(ctx, code, LValAccum, errs);
                code.add(labelEnd);
                return regAccum;

            default:
                throw new IllegalStateException();
            }
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        if (isConstant())
            {
            LVal.assign(toConstant(), code, errs);
            return;
            }

        if (LVal.isNormalVariable())
            {
            switch (combine(expr1.toConstant(), getOperatorString(), expr1.toConstant()))
                {
                case UorF:
                case UandT:
                    // result is the same as the result of the first expression
                    expr1.generateAssignment(ctx, code, LVal, errs);
                    return;

                case ForU:
                case TandU:
                    // result is the same as the result of the second expression
                    expr2.generateAssignment(ctx, code, LVal, errs);
                    return;
                }
            }

        super.generateAssignment(ctx, code, LVal, errs);
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return the default name of the "@Op" method
     */
    public String getDefaultMethodName()
        {
        // it uses the same operator method name as the non-conditional operator, but this
        // expression type short-circuits
        return isAnd()
                ? "and"
                : "or";
        }

    /**
     * @return the default operator symbol for the "@Op" method
     */
    public String getOperatorString()
        {
        // it uses the same operator method name as the non-conditional operator, but this
        // expression type short-circuits
        return isAnd()
                ? "&"
                : "|";
        }

    private int combine(Constant const1, String sOp, Constant const2)
        {
        int n;

        final Constant TRUE  = pool().valTrue();
        final Constant FALSE = pool().valFalse();

        if (Handy.equals(const1, TRUE))
            {
            n = '1';
            }
        else if (Handy.equals(const1, FALSE))
            {
            n = '0';
            }
        else
            {
            n = '?';
            }

        n = (n << 16) | (sOp.charAt(0) << 8);

        if (Handy.equals(const2, TRUE))
            {
            n |= '1';
            }
        else if (Handy.equals(const2, FALSE))
            {
            n |= '0';
            }
        else
            {
            n |= '?';
            }

        return n;
        }

    static final int ForF  = ('0' << 16) | ('|' << 8) | '0';
    static final int ForT  = ('0' << 16) | ('|' << 8) | '1';
    static final int ForU  = ('0' << 16) | ('|' << 8) | '?';
    static final int TorF  = ('1' << 16) | ('|' << 8) | '0';
    static final int TorT  = ('1' << 16) | ('|' << 8) | '1';
    static final int TorU  = ('1' << 16) | ('|' << 8) | '?';
    static final int UorF  = ('?' << 16) | ('|' << 8) | '0';
    static final int UorT  = ('?' << 16) | ('|' << 8) | '1';
    static final int UorU  = ('?' << 16) | ('|' << 8) | '?';
    static final int FandF = ('0' << 16) | ('&' << 8) | '0';
    static final int FandT = ('0' << 16) | ('&' << 8) | '1';
    static final int FandU = ('0' << 16) | ('&' << 8) | '?';
    static final int TandF = ('1' << 16) | ('&' << 8) | '0';
    static final int TandT = ('1' << 16) | ('&' << 8) | '1';
    static final int TandU = ('1' << 16) | ('&' << 8) | '?';
    static final int UandF = ('?' << 16) | ('&' << 8) | '0';
    static final int UandT = ('?' << 16) | ('&' << 8) | '1';
    static final int UandU = ('?' << 16) | ('&' << 8) | '?';
    }