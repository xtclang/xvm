package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Argument;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.JumpNull;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Context.Branch;

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
            TypeExpression exprType = new NullableTypeExpression(
                    expr.toTypeExpression(), getEndPosition());
            exprType.setParent(getParent());
            return exprType;
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
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        if (typeRequired != null && typeRequired.isTypeOfType())
            {
            TypeFit fit = toTypeExpression().testFit(ctx, typeRequired, ErrorListener.BLACKHOLE);
            if (fit.isFit())
                {
                return fit;
                }
            }
        return super.testFit(ctx, typeRequired, errs);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        if (typeRequired != null && typeRequired.isTypeOfType())
            {
            Expression exprType = validateAsType(ctx, typeRequired, errs);
            if (exprType != null)
                {
                return exprType;
                }
            }

        TypeConstant typeRequest = typeRequired == null ? null : typeRequired.ensureNullable();
        Expression   exprNew     = expr.validate(ctx, typeRequest, errs);
        if (exprNew == null)
            {
            return null;
            }

        expr = exprNew;

        TypeConstant type = exprNew.getType();

        // the second check is for not-nullable type that is still allowed to be assigned from null
        // (e.g. Object or Const)
        if (!type.isNullable() && !pool().typeNull().isA(type.resolveConstraints()))
            {
            exprNew.log(errs, Severity.ERROR, Compiler.ELVIS_NOT_NULLABLE);
            return replaceThisWith(exprNew);
            }

        AstNode parent = getParent();

        if (!parent.allowsShortCircuit(this))
            {
            exprNew.log(errs, Severity.ERROR, Compiler.SHORT_CIRCUIT_ILLEGAL);
            return null;
            }

        if (exprNew.isConstantNull())
            {
            exprNew.log(errs, Severity.ERROR, Compiler.SHORT_CIRCUIT_ALWAYS_NULL);
            }

        m_labelShort = parent.ensureShortCircuitLabel(this, ctx);

        TypeConstant typeNotNull = type.removeNullable();

        if (exprNew instanceof NameExpression)
            {
            NameExpression exprName = (NameExpression) exprNew;
            if (exprName.left == null)
                {
                String   sName = exprName.getName();
                Argument arg   = ctx.getVar(sName);
                if (arg instanceof Register)
                    {
                    TypeConstant typeCurr = arg.getType();

                    if (!typeCurr.isA(typeNotNull))
                        {
                        assert typeNotNull.isA(typeCurr);

                        Register regCurr = (Register) arg;

                        // add the narrowing for this context and safe off the current register
                        ctx.narrowLocalRegister(sName, regCurr, Branch.Always, typeNotNull);
                        m_labelShort.addRestore(sName, regCurr);
                        }
                    }
                }
            }

        return finishValidation(ctx, typeRequired, typeNotNull, TypeFit.Fit, null, errs);
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
        TypeConstant typeExpr = getType();
        if (isConstant() || pool().typeNull().isA(typeExpr.resolveConstraints()))
            {
            return super.generateArgument(ctx, code, fLocalPropOk, fUsedOnce, errs);
            }

        TypeConstant typeTemp = typeExpr.ensureNullable();
        Assignable   var      = createTempVar(code, typeTemp, false, errs);
        generateAssignment(ctx, code, var, errs);
        return var.getRegister();
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        if (isConstant() || !LVal.isLocalArgument() ||
                !pool().typeNull().isA(LVal.getType().resolveConstraints()))
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
        return expr + operator.getId().TEXT;
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
