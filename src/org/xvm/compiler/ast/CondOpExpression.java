package org.xvm.compiler.ast;


import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Argument;
import org.xvm.asm.Op;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Var;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Statement.Context;
import org.xvm.util.Handy;


/**
 * Conditional operator expressions "||" and "&&".
 *
 * <ul>
 * <li><tt>COND_OR:    "||"</tt> - </li>
 * <li><tt>COND_AND:   "&&"</tt> - </li>
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
    public boolean isAborting()
        {
        // these can complete if the first expression can complete, because the result can
        // be calculated from the first expression, depending on what its answer is; thus
        // the expression aborts if the first of the two expressions aborts
        return expr1.isAborting();
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

        Expression expr2Old = expr2;
        Expression expr2New = expr2Old.validate(ctx, typeBoolean, errs);
        Constant   const2   = null;
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

        return finishValidation(typeRequired, typeBoolean, fit, constResult, errs);
        }

    @Override
    public Argument generateArgument(Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        if (hasConstantValue())
            {
            return toConstant();
            }

        switch (combine(expr1.toConstant(), getOperatorString(), expr1.toConstant()))
            {
            case UorF:
            case UandT:
                // result is the same as the result of the first expression
                return expr1.generateArgument(code, fLocalPropOk, fUsedOnce, errs);

            case ForU:
            case TandU:
                // result is the same as the result of the second expression
                return expr2.generateArgument(code, fLocalPropOk, fUsedOnce, errs);

            case UorU:
            case UandU:
                code.add(new V) // TODO
                Register regAccum = new Register(getType());
                Register reg2 = new Register(getType(), Op.A_STACK);
                Register regResult = fUsedOnce ? new Register(getType(), Op.A_STACK) : new Register(getType())
                break;

            default:
                throw new IllegalStateException();
            }

        // REVIEW
        code.add(new Var(getType()));
        Register regResult = code.lastRegister();
        generateAssignment(code, new Assignable(regResult), errs);
        return regResult;
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        if (LVal.isLocalArgument()) // REVIEW what other options are there?
            {
            // evaluate the sub-expressions
            Argument arg1 = expr1.generateArgument(code, false, false, errs);
            Argument arg2 = expr2.generateArgument(code, false, false, errs);

            // generate the op that combines the two sub-expressions
            if (isAnd())
                {
                // TODO
                throw new UnsupportedOperationException();
                }
            else
                {
                // TODO
                throw new UnsupportedOperationException();
                }
            }
        else
            {
            super.generateAssignment(code, LVal, errs);
            }
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

    int combine(Constant const1, String sOp, Constant const2)
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


    // ----- fields --------------------------------------------------------------------------------

    }
