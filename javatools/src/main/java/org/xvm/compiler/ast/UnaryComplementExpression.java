package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.GP_Compl;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;


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
        // REVIEW the assumption is that a "not" of T results in a value of T; do we need to check the @Op?
        // REVIEW (if that change is made, then move that functionality up to PrefixExpression for UnaryMinus)
        return operator.getId() == Id.NOT
                ? pool().typeBoolean()
                : expr.getImplicitType(ctx);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        TypeFit      fit       = TypeFit.Fit;
        Constant     constVal  = null;
        Expression   exprRight = expr;
        TypeConstant typeRight = null;
        boolean      fBoolean  = false;

        if (operator.getId() == Id.NOT)
            {
            // the "!" operator only applies to a boolean
            typeRight = pool().typeBoolean();
            fBoolean  = true;
            }
        else if (typeRequired != null && exprRight.testFit(ctx, typeRequired, false, null).isFit()
                && !typeRequired.ensureTypeInfo(errs).findOpMethods("not", "~", 0).isEmpty())
            {
            typeRight = typeRequired;
            fBoolean  = typeRight.isA(pool().typeBoolean());
            }

        if (fBoolean)
            {
            ctx = ctx.enterNot();
            }
        typeRight = findBestOp(ctx, typeRequired, typeRight, "not", "~", errs);
        if (fBoolean)
            {
            ctx = ctx.exit();
            }

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
            code.add(new GP_Compl(arg, LVal.getLocalArgument()));
            }
        else
            {
            super.generateAssignment(ctx, code, LVal, errs);
            }
        }
    }