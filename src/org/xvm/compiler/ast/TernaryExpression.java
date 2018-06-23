package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Jump;
import org.xvm.asm.op.Label;

import org.xvm.compiler.ast.Statement.Context;


/**
 * A ternary expression is the "a ? b : c" expression.
 */
public class TernaryExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public TernaryExpression(Expression cond, Expression exprThen, Expression exprElse)
        {
        this.cond     = cond;
        this.exprThen = exprThen;
        this.exprElse = exprElse;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public long getStartPosition()
        {
        return cond.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return exprElse.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------


    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        return BiExpression.selectType(exprThen.getImplicitType(ctx),
                exprElse.getImplicitType(ctx), ErrorListener.BLACKHOLE);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        TypeFit      fit         = TypeFit.Fit;
        ConstantPool pool        = pool();
        Expression   exprNewCond = cond.validate(ctx, pool.typeBoolean(), errs);
        if (exprNewCond == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            cond = exprNewCond;
            // TODO check if it is short circuiting
            }

        TypeConstant typeRequest = typeRequired == null
                ? getImplicitType(ctx)
                : typeRequired;
        Expression   exprNewThen = exprThen.validate(ctx, typeRequest, errs);
        TypeConstant typeThen    = null;
        if (exprNewThen == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            exprThen = exprNewThen;
            typeThen = exprNewThen.getType();
            // TODO check if it is short circuiting

            if (typeRequest == null)
                {
                typeRequest = BiExpression.selectType(exprNewThen.getType(), null, errs);
                }
            }

        Expression   exprNewElse = exprElse.validate(ctx, typeRequest, errs);
        TypeConstant typeElse    = null;
        if (exprNewElse == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            exprElse = exprNewElse;
            typeElse = exprNewElse.getType();
            // TODO check if it is short circuiting
            }

        if (fit.isFit() && exprNewCond.hasConstantValue())
            {
            return exprNewCond.toConstant().equals(pool.valTrue())
                    ? exprNewThen
                    : exprNewElse;
            }

        TypeConstant typeResult = BiExpression.selectType(typeThen, typeElse, errs);
        return finishValidation(typeRequired, typeResult, fit, null, errs);
        }

    @Override
    public boolean isAssignable()
        {
        return exprThen.isAssignable() && exprElse.isAssignable();
        }

    @Override
    public boolean isAborting()
        {
        return cond.isAborting() || exprThen.isAborting() || exprElse.isAborting();
        }

    @Override
    public boolean isShortCircuiting()
        {
        // REVIEW cond.isShortCircuiting() || exprThen.isShortCircuiting() || exprElse.isShortCircuiting();
        return false;
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        Label labelElse = new Label("else");
        Label labelEnd  = new Label("end");

        cond.generateConditionalJump(code, labelElse, false, errs);
        exprThen.generateAssignment(code, LVal, errs);
        code.add(new Jump(labelEnd));
        code.add(labelElse);
        exprElse.generateAssignment(code, LVal, errs);
        code.add(labelEnd);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(cond)
          .append(" ? ")
          .append(exprThen)
          .append(" : ")
          .append(exprElse);

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression cond;
    protected Expression exprThen;
    protected Expression exprElse;

    private static final Field[] CHILD_FIELDS = fieldsForNames(TernaryExpression.class, "cond", "exprThen", "exprElse");
    }
