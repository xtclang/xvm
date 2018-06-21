package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.IIP_Dec;
import org.xvm.asm.op.IIP_Inc;
import org.xvm.asm.op.IIP_PostDec;
import org.xvm.asm.op.IIP_PostInc;
import org.xvm.asm.op.IIP_PreDec;
import org.xvm.asm.op.IIP_PreInc;
import org.xvm.asm.op.IP_Dec;
import org.xvm.asm.op.IP_Inc;
import org.xvm.asm.op.IP_PostDec;
import org.xvm.asm.op.IP_PostInc;
import org.xvm.asm.op.IP_PreDec;
import org.xvm.asm.op.IP_PreInc;
import org.xvm.asm.op.PIP_Dec;
import org.xvm.asm.op.PIP_Inc;
import org.xvm.asm.op.PIP_PostDec;
import org.xvm.asm.op.PIP_PostInc;
import org.xvm.asm.op.PIP_PreDec;
import org.xvm.asm.op.PIP_PreInc;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Statement.Context;


/**
 * The "++" or "--" that precedes or follows an assignable expression of type Sequential.
 */
public class SequentialAssignExpression
        extends PrefixExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public SequentialAssignExpression(Token operator, Expression expr)
        {
        super(operator, expr);
        assert operator.getId() == Id.INC || operator.getId() == Id.DEC;
        m_fPre = true;
        }

    public SequentialAssignExpression(Expression expr, Token operator)
        {
        super(operator, expr);
        assert operator.getId() == Id.INC || operator.getId() == Id.DEC;
        m_fPre = false;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff this is a pre-inc or pre-dec; false iff this is a post-inc or  post-dec
     */
    public boolean isPre()
        {
        return m_fPre;
        }

    /**
     * @return true iff this is a pre-inc or post-inc; false iff this is a pre-dec or  post-dec
     */
    public boolean isInc()
        {
        return operator.getId() == Id.INC;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        return expr.getImplicitType(ctx);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        TypeFit      fit  = TypeFit.Fit;
        TypeConstant type = null;


        TypeConstant typeSequential = pool().typeSequential();
        TypeConstant typeRequest    = typeRequired != null && typeRequired.isA(typeSequential)
                ? typeRequired
                : typeSequential;
        Expression exprNew = expr.validate(ctx, typeRequest, errs);
        if (exprNew == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            expr = exprNew;
            type = exprNew.getType();
            exprNew.requireAssignable(errs);
            // TODO verify that there is a next/prev method that produces the same type
            }

        return finishValidation(typeRequired, type, fit, null, errs);
        }

    private Assignable ensureTarget(Code code, ErrorListener errs)
        {
        Assignable LVal = m_LValTarget;
        if (LVal == null)
            {
            m_LValTarget = LVal = expr.generateAssignable(code, errs);
            assert LVal.getForm() != Assignable.BlackHole;
            }
        return LVal;
        }

    @Override
    public void generateVoid(Code code, ErrorListener errs)
        {
        Assignable LVal = ensureTarget(code, errs);
        switch (LVal.getForm())
            {
            case Assignable.LocalVar:
                code.add(isInc()
                        ? new IP_Inc(LVal.getRegister())
                        : new IP_Dec(LVal.getRegister()));
                break;

            case Assignable.LocalProp:
                code.add(isInc()
                        ? new IP_Inc(LVal.getProperty())
                        : new IP_Dec(LVal.getProperty()));
                break;

            case Assignable.TargetProp:
                code.add(isInc()
                        ? new PIP_Inc(LVal.getProperty(), LVal.getTarget())
                        : new PIP_Dec(LVal.getProperty(), LVal.getTarget()));
                break;

            case Assignable.Indexed:
                code.add(isInc()
                        ? new IIP_Inc(LVal.getArray(), LVal.getIndex())
                        : new IIP_Dec(LVal.getArray(), LVal.getIndex()));
                break;

            case Assignable.IndexedN:
                // TODO
                throw notImplemented();

            case Assignable.IndexedProp:
                code.add(isInc()
                        ? new IIP_Inc(LVal.getProperty(), LVal.getIndex())
                        : new IIP_Dec(LVal.getProperty(), LVal.getIndex()));
                break;

            case Assignable.IndexedNProp:
                // TODO
                throw notImplemented();

            case Assignable.BlackHole:
            default:
                throw new IllegalStateException();
            }
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        switch (LVal.getForm())
            {
            case Assignable.BlackHole:
                generateVoid(code, errs);
                break;

            case Assignable.LocalVar:
            case Assignable.LocalProp:
                Argument   argReturn  = LVal.getLocalArgument();
                Assignable LValTarget = ensureTarget(code, errs);
                switch (LValTarget.getForm())
                    {
                    case Assignable.LocalVar:
                    case Assignable.LocalProp:
                        {
                        Argument argTarget = LValTarget.getLocalArgument();
                        code.add(isPre()
                                ? isInc()
                                        ? new IP_PreInc(argTarget, argReturn)       // ++x
                                        : new IP_PreDec(argTarget, argReturn)       // --x
                                : isInc()
                                        ? new IP_PostInc(argTarget, argReturn)      // x++
                                        : new IP_PostDec(argTarget, argReturn));    // x--
                        }
                        break;

                    case Assignable.TargetProp:
                        {
                        Argument         argTarget  = LValTarget.getLocalArgument();
                        PropertyConstant propTarget = LValTarget.getProperty();
                        code.add(isPre()
                                ? isInc()
                                        ? new PIP_PreInc(propTarget, argTarget, argReturn)
                                        : new PIP_PreDec(propTarget, argTarget, argReturn)
                                : isInc()
                                        ? new PIP_PostInc(propTarget, argTarget, argReturn)
                                        : new PIP_PostDec(propTarget, argTarget, argReturn));
                        }
                        break;

                    case Assignable.Indexed:
                    case Assignable.IndexedProp:
                        {
                        Argument argTarget = LValTarget.getLocalArgument();
                        Argument argIndex  = LValTarget.getIndex();
                        code.add(isPre()
                                ? isInc()
                                        ? new IIP_PreInc(argTarget, argIndex, argReturn)
                                        : new IIP_PreDec(argTarget, argIndex, argReturn)
                                : isInc()
                                        ? new IIP_PostInc(argTarget, argIndex, argReturn)
                                        : new IIP_PostDec(argTarget, argIndex, argReturn));
                        }
                        break;

                    case Assignable.IndexedN:
                        // TODO
                        throw notImplemented();

                    case Assignable.IndexedNProp:
                        // TODO
                        throw notImplemented();

                    case Assignable.BlackHole:
                    default:
                        throw new IllegalStateException();
                    }
                break;

            case Assignable.TargetProp:
            case Assignable.Indexed:
            case Assignable.IndexedN:
            case Assignable.IndexedProp:
            case Assignable.IndexedNProp:
                super.generateAssignment(code, LVal, errs);
                break;

            default:
                throw new IllegalStateException();
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    private boolean m_fPre;

    private transient Assignable m_LValTarget;
    }
