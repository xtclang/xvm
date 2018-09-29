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
    public TypeFit testFit(Context ctx, TypeConstant typeRequired)
        {
        TypeFit fit = expr1.testFit(ctx, typeRequired);
        if (fit.isFit())
            {
            fit.combineWith(expr2.testFit(ctx, typeRequired));
            }
        return fit;
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
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
        if (typeRequired != null && (type2Req == null || !expr2.testFit(ctx, type2Req).isFit()))
            {
            type2Req = typeRequired;
            }
        Expression expr2New = expr2.validate(ctx, type2Req, errs);
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

        return finishValidation(typeRequired, typeResult, fit, constVal, errs);
        }

    @Override
    public boolean isShortCircuiting()
        {
        // this expression "grounds" any short circuit that happens on the left side of the ":"
        return expr2.isShortCircuiting();
        }

    @Override
    public boolean isAborting()
        {
        // these can complete if the first expression can complete, because the result can
        // be calculated from the first expression, depending on what its answer is; thus
        // the expression aborts if the first of the two expressions aborts
        return expr1.isAborting();
        }

    @Override
    protected boolean allowsShortCircuit(Expression exprChild)
        {
        return exprChild == expr1 || super.allowsShortCircuit(exprChild);
        }

    @Override
    protected Label getShortCircuitLabel(Expression exprChild)
        {
        if (exprChild != expr1)
            {
            return super.getShortCircuitLabel(exprChild);
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
        if (isConstant() || !LVal.isNormalVariable())
            {
            super.generateAssignment(ctx, code, LVal, errs);
            return;
            }

        Label labelElse = getShortCircuitLabel(expr1);
        Label labelEnd  = new Label("end_:_" + m_nLabel);

        expr1.generateAssignment(ctx, code, LVal, errs);
        code.add(new Jump(labelEnd));
        code.add(labelElse);
        expr2.generateAssignment(ctx, code, LVal, errs);
        code.add(labelEnd);
        }


    // ----- fields --------------------------------------------------------------------------------

    private static    int   m_nCounter;
    private transient int   m_nLabel;
    private transient Label m_labelElse;
    }
