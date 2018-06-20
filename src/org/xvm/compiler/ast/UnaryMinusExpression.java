package org.xvm.compiler.ast;


import java.util.Set;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.GP_Neg;

import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.Severity;


/**
 * The "-" that precedes a number.
 */
public class UnaryMinusExpression
        extends PrefixExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public UnaryMinusExpression(Token operator, Expression expr)
        {
        super(operator, expr);
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
        Expression exprRight = expr;

        // in the case of a literal expression, we handle the unary minus by prepending it to the
        // literal
        if (exprRight instanceof LiteralExpression)
            {
            // can't do this twice
            assert !exprRight.isValidated();

            exprRight = ((LiteralExpression) exprRight).adoptUnaryPrefix(operator, errs);
            return exprRight.validate(ctx, typeRequired, errs);
            }

        // otherwise, this expression must apply the unary minus
        TypeFit      fit        = TypeFit.Fit;
        TypeConstant typeTarget = null;
        Constant     constVal   = null;
        if (typeRequired != null && !typeRequired.ensureTypeInfo(errs).findOpMethods("neg", "-#", 0).isEmpty())
            {
            typeTarget = typeRequired;
            }
        exprRight = exprRight.validate(ctx, typeTarget, errs);
        if (exprRight == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            expr       = exprRight;
            typeTarget = exprRight.getType();

            Set<MethodConstant> setOps = typeTarget.ensureTypeInfo(errs).findOpMethods("neg", "-#", 0);
            if (setOps.isEmpty())
                {
                fit = TypeFit.NoFit;
                log(errs, Severity.ERROR, org.xvm.compiler.Compiler.MISSING_OPERATOR,
                        operator.getValueText(), typeTarget.getValueString());
                }
            else
                {
                if (setOps.size() > 1)
                    {
                    // TODO pick the best one, otherwise log an error (current naive implementation just grabs the first one)
                    // fit = TypeFit.NoFit;
                    // log(errs, Severity.ERROR, org.xvm.compiler.Compiler.AMBIGUOUS_OPERATOR_SIGNATURE,
                    //         operator.getValueText(), typeTarget.getValueString());
                    }
                m_idOp     = setOps.iterator().next();
                typeTarget = m_idOp.getSignature().getRawReturns()[0];
                if (fit.isFit() && exprRight.hasConstantValue())
                    {
                    try
                        {
                        constVal = exprRight.toConstant().apply(operator.getId(), null);
                        }
                    catch (RuntimeException e) {}
                    }
                }

            }
        return finishValidation(typeRequired, typeTarget, fit, constVal, errs);
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        if (LVal.isLocalArgument())
            {
            Argument arg = expr.generateArgument(code, true, true, errs);
            code.add(new GP_Neg(arg, LVal.getLocalArgument()));
            }
        else
            {
            super.generateAssignment(code, LVal, errs);
            }
        }

    // ----- fields --------------------------------------------------------------------------------

    private transient MethodConstant m_idOp;
    }
