package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Argument;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.JumpNull;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.util.Severity;


/**
 * A short-circuiting expression for testing if a sub-expression is null.
 * <p/>
 * <pre>
 *     PostfixExpression NoWhitespace "?"
 * </pre>
 */
public class NotNullExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public NotNullExpression(Expression expr, Token operator)
        {
        this.expr     = expr;
        this.operator = operator;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public TypeExpression toTypeExpression()
        {
        if (operator.getId() == Token.Id.COND)
            {
            // convert "expr?" to "type?"
            return new NullableTypeExpression(expr.toTypeExpression(), getEndPosition());
            }

        return super.toTypeExpression();
        }

    @Override
    public long getStartPosition()
        {
        return expr.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return operator.getEndPosition();
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
        TypeConstant type = expr.getImplicitType(ctx);
        return type == null
                ? null
                : type.removeNullable(pool());
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        ConstantPool pool = pool();
        TypeFit      fit  = TypeFit.Fit;
        TypeConstant type = null;

        TypeConstant typeRequest = typeRequired == null
                ? null
                : typeRequired.ensureNullable(pool);
        Expression   exprNew     = expr.validate(ctx, typeRequest, errs);
        if (exprNew == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            expr = exprNew;
            type = exprNew.getType();

            // the second check is for not-nullable type that is still allowed to be assigned from null
            // (e.g. Object or Const)
            if (!type.isNullable() && !pool.typeNull().isA(type))
                {
                exprNew.log(errs, Severity.ERROR, Compiler.ELVIS_NOT_NULLABLE);
                return replaceThisWith(exprNew);
                }

            if (!getParent().allowsShortCircuit(this))
                {
                exprNew.log(errs, Severity.ERROR, Compiler.SHORT_CIRCUIT_ILLEGAL);
                return null;
                }

            if (exprNew.isConstantNull())
                {
                exprNew.log(errs, Severity.ERROR, Compiler.SHORT_CIRCUIT_ALWAYS_NULL);
                }

            type         = type.removeNullable(pool);
            m_labelShort = getParent().ensureShortCircuitLabel(this, ctx);
            }

        return finishValidation(typeRequired, type, fit, null, errs);
        }

    @Override
    public boolean isShortCircuiting()
        {
        return true;
        }

    @Override
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        ConstantPool pool     = pool();
        TypeConstant typeExpr = getType();
        if (isConstant() || pool.typeNull().isA(typeExpr))
            {
            return super.generateArgument(ctx, code, fLocalPropOk, fUsedOnce, errs);
            }

        TypeConstant typeTemp = typeExpr.ensureNullable(pool);
        Assignable   var      = createTempVar(code, typeTemp, false, errs);
        generateAssignment(ctx, code, var, errs);
        return var.getRegister().narrowType(typeExpr);
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        if (isConstant() || !LVal.isLocalArgument() || !pool().typeNull().isA(LVal.getType()))
            {
            super.generateAssignment(ctx, code, LVal, errs);
            return;
            }

        expr.generateAssignment(ctx, code, LVal, errs);
        code.add(new JumpNull(LVal.getLocalArgument(), m_labelShort));
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(expr)
          .append(operator.getId().TEXT);

        return sb.toString();
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression expr;
    protected Token      operator;

    protected transient Label m_labelShort;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NotNullExpression.class, "expr");
    }
