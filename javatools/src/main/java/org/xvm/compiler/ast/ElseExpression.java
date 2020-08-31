package org.xvm.compiler.ast;


import org.xvm.asm.Constant;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Jump;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


/**
 * The trailing "else" expression for any short-circuited expressions that precede it:
 *
 * <ul>
 * <li><tt>COLON:      ":"</tt> - an "else" for nullability checks</li>
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


    // ----- accessors -----------------------------------------------------------------------------


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        TypeConstant type1 = expr1.getImplicitType(ctx);
        TypeConstant type2 = expr2.getImplicitType(ctx);
        if (type1 == null || type2 == null)
            {
            return null;
            }

        TypeConstant typeResult = Op.selectCommonType(type1, type2, ErrorListener.BLACKHOLE);
        return typeResult == null
                ? pool().ensureIntersectionTypeConstant(type1, type2)
                : typeResult;
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        TypeFit fit = expr1.testFit(ctx, typeRequired, errs);
        if (fit.isFit())
            {
            fit.combineWith(expr2.testFit(ctx, typeRequired, errs));
            }
        return fit;
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        // TODO CP: we need to fork the context and collect "short-circuit" branches
        //          during the ensureShortCircuitLabel() call
        TypeFit      fit      = TypeFit.Fit;
        Expression   expr1New = expr1.validate(ctx, typeRequired, errs);
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

        TypeConstant type2Req = type1 == null ? null : Op.selectCommonType(type1, null, errs);
        if (typeRequired != null && (type2Req == null || !expr2.testFit(ctx, type2Req, null).isFit()))
            {
            type2Req = typeRequired;
            }

        // TODO CP: this is a temporary solution; simply ignore the impact of the "else"
        Context ctx2 = ctx.enter();
        Expression expr2New = expr2.validate(ctx2, type2Req, errs);
        if (expr2New == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            expr2 = expr2New;
            }

        ctx2.discard();

        if (!fit.isFit())
            {
            return finishValidation(ctx, typeRequired, null, fit, null, errs);
            }

        if (!expr1New.isShortCircuiting())
            {
            expr1New.log(errs, Severity.ERROR, Compiler.SHORT_CIRCUIT_REQUIRED);
            return replaceThisWith(expr1New);
            }

        TypeConstant type2      = expr2New.getType();
        TypeConstant typeResult = Op.selectCommonType(type1, type2, errs);
        if (typeResult == null)
            {
            typeResult = pool().ensureIntersectionTypeConstant(type1, type2);
            }

        Constant constVal = null;
        if (expr1New.isConstant())
            {
            constVal = expr1New.toConstant();
            }

        if (m_labelElse != null)
            {
            m_labelElse.restoreNarrowed(ctx);
            }

        return finishValidation(ctx, typeRequired, typeResult, fit, constVal, errs);
        }

    @Override
    public boolean isShortCircuiting()
        {
        // this expression "grounds" any short circuit that happens on the left side of the ":"
        return expr2.isShortCircuiting();
        }

    @Override
    public boolean isCompletable()
        {
        // these can complete if the first expression can complete, because the result can
        // be calculated from the first expression, depending on what its answer is
        return expr1.isCompletable();
        }

    @Override
    protected boolean allowsShortCircuit(AstNode nodeChild)
        {
        return nodeChild == expr1 || super.allowsShortCircuit(nodeChild);
        }

    @Override
    protected Label ensureShortCircuitLabel(AstNode nodeOrigin, Context ctxOrigin)
        {
        AstNode nodeChild = findChild(nodeOrigin);
        if (nodeChild != expr1)
            {
            assert nodeChild == expr2;
            return super.ensureShortCircuitLabel(nodeOrigin, ctxOrigin);
            }

        // generate a "grounding" target label for the "left side child expression"
        Label label = m_labelElse;
        if (label == null)
            {
            m_nLabel    = ++m_nCounter;
            m_labelElse = label = new Label("else_:_" + m_nLabel);
            }
        return label;
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        if (isConstant())
            {
            super.generateAssignment(ctx, code, LVal, errs);
            return;
            }

        expr1.generateAssignment(ctx, code, LVal, errs);

        if (m_labelElse != null)
            {
            Label labelEnd = new Label("end_:_" + m_nLabel);
            code.add(new Jump(labelEnd));
            code.add(m_labelElse);
            expr2.generateAssignment(ctx, code, LVal, errs);
            code.add(labelEnd);
            }
        }


    // ----- fields --------------------------------------------------------------------------------

    private static    int   m_nCounter;
    private transient int   m_nLabel;
    private transient Label m_labelElse;
    }
