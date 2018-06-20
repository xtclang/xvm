package org.xvm.compiler.ast;


import java.util.Set;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.GP_Compl;

import org.xvm.compiler.Token;

import org.xvm.compiler.Token.Id;
import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.Severity;


/**
 * The "~" that precedes a value (or "!" for a Boolean).
 */
public class UnaryComplementExpression
        extends PrefixExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public UnaryComplementExpression(Token operator, Expression expr)
        {
        super(operator, expr);
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        return operator.getId() == Id.NOT
                ? pool().typeBoolean()
                : expr.getImplicitType(ctx);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        TypeFit      fit       = TypeFit.Fit;
        Expression   exprRight = expr;
        TypeConstant typeRight = null;
        Constant     constVal  = null;
        if (operator.getId() == Id.NOT)
            {
            // the "!" operator only applies to a boolean
            typeRight = pool().typeBoolean();
            }
        else if (typeRequired != null && exprRight.testFit(ctx, typeRequired).isFit()
                && !typeRequired.ensureTypeInfo(errs).findOpMethods("not", "~", 0).isEmpty())
            {
            typeRight = typeRequired;
            }
        exprRight = exprRight.validate(ctx, typeRight, errs);
        if (exprRight == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            expr       = exprRight;
            typeRight = exprRight.getType();

            Set<MethodConstant> setOps = typeRight.ensureTypeInfo(errs).findOpMethods("not", "~", 0);
            if (setOps.isEmpty())
                {
                fit = TypeFit.NoFit;
                log(errs, Severity.ERROR, org.xvm.compiler.Compiler.MISSING_OPERATOR,
                        operator.getValueText(), typeRight.getValueString());
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
                typeRight = m_idOp.getSignature().getRawReturns()[0];
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
        return finishValidation(typeRequired, typeRight, fit, constVal, errs);
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        if (LVal.isLocalArgument())
            {
            Argument arg = expr.generateArgument(code, true, true, errs);
            code.add(new GP_Compl(arg, LVal.getLocalArgument()));
            }
        else
            {
            super.generateAssignment(code, LVal, errs);
            }
        }

    // ----- fields --------------------------------------------------------------------------------

    private transient MethodConstant m_idOp;
    }
