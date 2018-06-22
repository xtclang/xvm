package org.xvm.compiler.ast;


import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Statement.Context;


/**
 * One of the "else" expression types:
 *
 * <ul>
 * <li><tt>COLON:      ":"</tt> - an "else" for nullability checks</li>
 * <li><tt>COND_ELSE:  "?:"</tt> - the "elvis" operator</li>
 * </ul>
 */
public class ElseExpression
        extends BiExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public ElseExpression(Expression expr1, Token operator, Expression expr2)
        {
        super(expr1, operator, expr2);
        }


    // ----- compilation ---------------------------------------------------------------------------


    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        TypeConstant type1 = expr1.getImplicitType(ctx);
        TypeConstant type2 = expr2.getImplicitType(ctx);

        if (type1 != null)
            {
            type1 = type1.removeNullable();
            }

        return selectType(type1, type2, ErrorListener.BLACKHOLE);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        TypeFit      fit      = TypeFit.Fit;
        ConstantPool pool     = pool();
        Expression   expr1New = expr1.validate(ctx, null, errs);
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

        TypeConstant type2Req = type1 == null ? null : selectType(type1.removeNullable(), null, errs);
        Expression   expr2New = expr2.validate(ctx, type2Req, errs);
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
            return finishValidation(typeRequired, null, fit, null, errs);
            }

        if (type1.isOnlyNullable())
            {
            // TODO log error only nullable
            return expr2New;
            }

        if (!type1.isNullable() && !pool.typeNull().isA(type1))
            {
            // TODO log error not nullable
            return expr1New;
            }


        TypeConstant type2      = expr2New.getType();
        TypeConstant typeResult = selectType(type1.removeNullable(), type2, errs);
        Constant     constVal   = null;
        if (expr1.hasConstantValue())
            {
            // TODO calculate the constant value
            }

        return finishValidation(typeRequired, typeResult, fit, constVal, errs);
        }

    @Override
    public boolean isShortCircuiting()
        {
        // with the colon operator, we know that expr1 has to be short-circuiting (or it's a
        // compiler error); all other operators are considered to be short circuiting if either
        // sub-expression is short-circuiting
        return operator.getId() == Id.COLON
                ? expr2.isShortCircuiting()
                : expr1.isShortCircuiting() || expr2.isShortCircuiting();
        }

    @Override
    public boolean isAborting()
        {
        // these can complete if the first expression can complete, because the result can
        // be calculated from the first expression, depending on what its answer is; thus
        // the expression aborts if the first of the two expressions aborts
        return expr1.isAborting();
        }


    // ----- fields --------------------------------------------------------------------------------

    }
