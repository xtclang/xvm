package org.xvm.compiler.ast;


import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


/**
 * The "+" that precedes a number.
 */
public class UnaryPlusExpression
        extends PrefixExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public UnaryPlusExpression(Token operator, Expression expr)
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
        boolean    fPrepend  = exprRight instanceof LiteralExpression;
        if (fPrepend)
            {
            // can't do this twice
            assert !exprRight.isValidated();

            // in the case of a literal expression, we handle the unary plus by prepending it to the
            // literal
            exprRight = ((LiteralExpression) exprRight).adoptUnaryPrefix(operator, errs);
            }

        exprRight = exprRight.validate(ctx, typeRequired, errs);

        if (exprRight != null && !fPrepend)
            {
            // in all other cases besides a literal expression, the unary plus is ignored, except
            // for one thing: the right side MUST refer to a number, or we need to log an error
            TypeConstant typeRight = exprRight.getType();
            if (!typeRight.isA(pool().typeNumber()))
                {
                log(errs, Severity.ERROR, Compiler.MISSING_OPERATOR,
                        operator.getValueText(), typeRight.getValueString());
                return null;
                }
            }

        return replaceThisWith(exprRight);
        }
    }
