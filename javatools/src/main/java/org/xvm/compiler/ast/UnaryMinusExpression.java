package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.GP_Neg;

import org.xvm.compiler.Token;


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
            exprRight = exprRight.validate(ctx, typeRequired, errs);

            return exprRight == null
                ? null // an error must've been reported
                : replaceThisWith(exprRight);
            }

        // otherwise, this expression must apply the unary minus as an op
        TypeFit  fit      = TypeFit.Fit;
        Constant constVal = null;

        // if there is a type that is being requested that we can convert to and satisfy this
        // operation, then do so (just like with binary ops, convert as "deep" in the AST tree as
        // possible)
        TypeConstant typeRight = null;
        if (typeRequired != null && exprRight.testFit(ctx, typeRequired, null).isFit()
                && !typeRequired.ensureTypeInfo(errs).findOpMethods("neg", "-#", 0).isEmpty())
            {
            typeRight = typeRequired;
            }

        typeRight = findBestOp(ctx, typeRequired, typeRight, "neg", "-#", errs);

        if (typeRight == null)
            {
            fit = TypeFit.NoFit;
            }
        else if (exprRight.isConstant())
            {
            try
                {
                constVal = exprRight.toConstant().apply(operator.getId(), null);
                }
            catch (RuntimeException ignore) {}
            }

        return finishValidation(ctx, typeRequired, typeRight, fit, constVal, errs);
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        if (LVal.isLocalArgument())
            {
            Argument arg = expr.generateArgument(ctx, code, true, true, errs);
            code.add(new GP_Neg(arg, LVal.getLocalArgument()));
            }
        else
            {
            super.generateAssignment(ctx, code, LVal, errs);
            }
        }
    }
