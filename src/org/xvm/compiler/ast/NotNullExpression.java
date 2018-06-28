package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Argument;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.JumpNull;

import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Statement.Context;

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
                : type.removeNullable();
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        TypeFit      fit  = TypeFit.Fit;
        TypeConstant type = null;

        TypeConstant typeRequest = typeRequired == null
                ? null
                : typeRequired.ensureNullable();
        Expression   exprNew     = expr.validate(ctx, typeRequest, errs);
        if (exprNew == null)
            {
            fit = TypeFit.NoFit;
            }
        else
            {
            expr = exprNew;
            type = exprNew.getType();
            if (pool().typeNull().isA(type))
                {
                type = type.removeNullable();
                }
            else
                {
                exprNew.log(errs, Severity.ERROR, org.xvm.compiler.Compiler.ELVIS_NOT_NULLABLE);
                return replaceThisWith(exprNew);
                }
            }

        return finishValidation(typeRequired, type, fit, null, errs);
        }

    @Override
    public boolean isShortCircuiting()
        {
        return true;
        }

    @Override
    public Argument generateArgument(Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        if (isConstant() || pool().typeNull().isA(getType()))
            {
            return super.generateArgument(code, fLocalPropOk, fUsedOnce, errs);
            }

        TypeConstant typeTemp = getType().ensureNullable();
        Assignable   var      = createTempVar(code, typeTemp, false, errs);
        generateAssignment(code, var, errs);
        return var.getRegister();
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        if (isConstant() || !LVal.isNormalVariable() || !pool().typeNull().isA(LVal.getType()))
            {
            super.generateAssignment(code, LVal, errs);
            return;
            }

        expr.generateAssignment(code, LVal, errs);
        code.add(new JumpNull(LVal.getRegister(), getShortCircuitLabel()));
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

    private static final Field[] CHILD_FIELDS = fieldsForNames(NotNullExpression.class, "expr");
    }
