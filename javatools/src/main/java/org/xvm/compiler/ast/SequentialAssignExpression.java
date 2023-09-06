package org.xvm.compiler.ast;


import java.util.Map;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.UnaryOpExprAST;
import org.xvm.asm.ast.UnaryOpExprAST.Operator;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;


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
     * @return true iff this is a pre-inc or pre-dec; false iff this is a post-inc or post-dec
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

    /**
     * @return the Sequential representing this expression
     */
    protected Sequential getSeq()
        {
        return isInc()
                ? isPre() ? Sequential.PreInc : Sequential.PostInc
                : isPre() ? Sequential.PreDec : Sequential.PostDec;
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
            exprNew.requireAssignable(ctx, errs);
            // TODO verify that there is a next/prev method that produces the required type
            exprNew.markAssignment(ctx, false, errs);
            }

        return finishValidation(ctx, typeRequired, type, fit, null, errs);
        }

    @Override
    public boolean isStandalone()
        {
        return true;
        }

    private Assignable ensureTarget(Context ctx, Code code, ErrorListener errs)
        {
        Assignable LVal = m_LValTarget;
        if (LVal == null)
            {
            m_LValTarget = LVal = expr.generateAssignable(ctx, code, errs);
            assert LVal.getForm() != AssignForm.BlackHole;
            }
        return LVal;
        }

    @Override
    public void generateVoid(Context ctx, Code code, ErrorListener errs)
        {
        Assignable LValTarget = ensureTarget(ctx, code, errs);
        LValTarget.assignSequential(isInc() ? Sequential.Inc : Sequential.Dec, null, false, code, errs);
        }

    @Override
    public Argument generateArgument(Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        Assignable LValTarget = ensureTarget(ctx, code, errs);
        return LValTarget.assignSequential(getSeq(), null, fUsedOnce, code, errs);
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        Assignable LValTarget = ensureTarget(ctx, code, errs);
        LValTarget.assignSequential(getSeq(), LVal, false, code, errs);
        }

    @Override
    public boolean isTraceworthy()
        {
        return true;
        }

    @Override
    protected void selectTraceableExpressions(Map<String, Expression> mapExprs)
        {
        // do not go inside of this expression (it's the result of this expression that matters)
        }

    @Override
    public ExprAST<Constant> getExprAST()
        {
        Operator op = isPre()
                ? isInc()
                    ? Operator.PreInc
                    : Operator.PreDec
                : isInc()
                    ? Operator.PostInc
                    : Operator.PostDec;

        return new UnaryOpExprAST<>(expr.getExprAST(), op, getType());
        }


    // ----- fields --------------------------------------------------------------------------------

    private final boolean m_fPre;

    private transient Assignable m_LValTarget;
    }