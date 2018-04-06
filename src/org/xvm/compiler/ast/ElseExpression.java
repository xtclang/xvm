package org.xvm.compiler.ast;


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
    protected Expression validate(Context ctx, TypeConstant typeRequired, TuplePref pref, ErrorListener errs)
        {
//            case COND_ELSE:
//                m_constType = expr1.getType().nonNullable();
//                if (fValid)
//                    {
//                    // the left side must be nullable, and the right expression must be assignable
//                    // to the non-nullable type of the left expression, otherwise we cannot
//                    // determine an "implicit type" (the error is deferred until the compilation
//                    // stage, so if the type is pushed to this expression, it can use that)
//                    // TODO
//                    }
//                break;

//            case COLON:
//                // the types have to be equal, or the right expression must be assignable to the
//                // type of the left expression, otherwise we cannot determine an "implicit type"
//                // (the error is deferred until the compilation stage, so if the type is pushed
//                // to this expression, it can use that, i.e. type inference)
//                m_constType = expr1.getType();
//                break;

        throw new UnsupportedOperationException();
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
