package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Argument;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Register;

import org.xvm.asm.ast.ExprAST;
import org.xvm.asm.ast.NotNullExprAST;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.JumpFalse;
import org.xvm.asm.op.JumpNull;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Context.Branch;

import org.xvm.util.Severity;


/**
 * A short-circuiting expression for testing if a sub-expression is null, and yielding the non-null
 * value if the sub-expression is not null.
 *
 * Experimental feature: Alternatively, this short-circuiting expression tests a "conditional"
 * expression (one that yields both a Boolean and at least one additional value), and short-circuits
 * iff that first Boolean value yielded is False, and otherwise yields the second value.
 * <p/>
 * <pre>
 *     PostfixExpression NoWhitespace "?"
 * </pre>
 *
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
        TypeConstant[] atype = expr.getImplicitTypes(ctx);
        switch (atype.length)
            {
            case 0:
                return null;

            case 1:
                return atype[0].removeNullable();

            default:
                TypeConstant type0 = atype[0];
                return type0.isA(pool().typeBoolean())
                        ? atype[1]
                        : type0.removeNullable();
            }
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, boolean fExhaustive, ErrorListener errs)
        {
        if (typeRequired != null)
            {
            if (typeRequired.isTypeOfType())
                {
                TypeFit fit = toTypeExpression().testFit(ctx, typeRequired, fExhaustive, ErrorListener.BLACKHOLE);
                if (fit.isFit())
                    {
                    return fit;
                    }
                }

            TypeFit fit = expr.testFitMulti(ctx, new TypeConstant[]{pool().typeBoolean(), typeRequired},
                    fExhaustive, ErrorListener.BLACKHOLE);
            if (fit.isFit())
                {
                return fit;
                }
            }

        return super.testFit(ctx, typeRequired, fExhaustive, errs);
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

        // at this point, we have to make a decision: either this expression takes a "T?" and strips
        // the null off and returns the T, or it takes a "conditional T" / "(Boolean, T)" and
        // returns the T
        ConstantPool   pool      = pool();
        boolean        fCond     = false;
        TypeConstant[] atypeCond = new TypeConstant[]{pool.typeBoolean(), pool.typeObject()};
        Expression     exprNew;
        if (expr.testFitMulti(ctx, atypeCond, true, ErrorListener.BLACKHOLE).isFit())
            {
            m_fCond = fCond = true;

            if (typeRequired != null)
                {
                atypeCond[1] = typeRequired;
                }
            exprNew = expr.validateMulti(ctx, atypeCond, errs);
            }
        else
            {
            TypeConstant typeRequest = typeRequired == null ? null : typeRequired.ensureNullable();
            exprNew = expr.validate(ctx, typeRequest, errs);
            }

        if (exprNew == null)
            {
            return null;
            }

        expr = exprNew;
        TypeConstant typeResult = fCond ? exprNew.getTypes()[1] : exprNew.getType();

        // the second check is for not-nullable type that is still allowed to be assigned from null
        // (e.g. Object or Const)
        if (!fCond && !typeResult.isNullable() && !pool().typeNull().isA(typeResult.resolveConstraints()))
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

        if (!fCond && exprNew.isConstantNull())
            {
            exprNew.log(errs, Severity.ERROR, Compiler.SHORT_CIRCUIT_ALWAYS_NULL);
            }

        m_labelShort = parent.ensureShortCircuitLabel(this, ctx);

        if (!fCond)
            {
            typeResult = typeResult.removeNullable();

            if (exprNew instanceof NameExpression exprName)
                {
                if (exprName.left == null)
                    {
                    String   sName = exprName.getName();
                    Argument arg   = ctx.getVar(sName);
                    if (arg instanceof Register)
                        {
                        TypeConstant typeCurr = arg.getType();

                        if (!typeCurr.isA(typeResult))
                            {
                            assert typeResult.isA(typeCurr);

                            Register regCurr = (Register) arg;

                            // add the narrowing for this context and save off the current register
                            ctx.narrowLocalRegister(sName, regCurr, Branch.Always, typeResult);
                            m_labelShort.addRestore(sName, regCurr);
                            }
                        }
                    }
                }
            }

        return finishValidation(ctx, typeRequired, typeResult, TypeFit.Fit, null, errs);
        }

    @Override
    public boolean isShortCircuiting()
        {
        return true;
        }

    @Override
    protected boolean allowsConditional(Expression exprChild)
        {
        return m_fCond;
        }

    @Override
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        TypeConstant typeExpr = getType();
        if (isConstant() || !m_fCond && pool().typeNull().isA(typeExpr.resolveConstraints()))
            {
            return super.generateArgument(ctx, code, fLocalPropOk, fUsedOnce, errs);
            }

        if (m_fCond)
            {
            Assignable   varCond = createTempVar(code, pool().typeBoolean(), true);
            Assignable   varVal  = createTempVar(code, getType(), false);
            Assignable[] LVals   = new Assignable[] {varCond, varVal};
            expr.generateAssignments(ctx, code, LVals, errs);
            code.add(new JumpFalse(varCond.getRegister(), m_labelShort));
            return varVal.getRegister();
            }
        else
            {
            TypeConstant typeTemp = typeExpr.ensureNullable();
            Assignable   var      = createTempVar(code, typeTemp, false);
            generateAssignment(ctx, code, var, errs);
            return var.getRegister().narrowType(typeExpr);
            }
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        if (isConstant() || m_fCond || !LVal.isLocalArgument() ||
                !pool().typeNull().isA(LVal.getType().resolveConstraints()))
            {
            super.generateAssignment(ctx, code, LVal, errs);
            return;
            }

        // generate a temporary argument to avoid overwriting the destination LVal with a potential
        // Null value
        Argument arg = expr.generateArgument(ctx, code, true, false, errs);
        code.add(new JumpNull(arg, m_labelShort));
        LVal.assign(arg, code, errs);
        }

    @Override
    public ExprAST getExprAST(Context ctx)
        {
        return new NotNullExprAST(expr.getExprAST(ctx), getType());
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

    /**
     * True iff the short-circuit operator is used to convert a "(Boolean, T)" into a "T".
     */
    private transient boolean m_fCond;
    private transient Label   m_labelShort;

    private static final Field[] CHILD_FIELDS = fieldsForNames(NotNullExpression.class, "expr");
    }