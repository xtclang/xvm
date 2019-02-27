package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.OpCondJump;
import org.xvm.asm.OpTest;

import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.asm.op.Cmp;
import org.xvm.asm.op.IsEq;
import org.xvm.asm.op.IsGt;
import org.xvm.asm.op.IsGte;
import org.xvm.asm.op.IsLt;
import org.xvm.asm.op.IsLte;
import org.xvm.asm.op.IsNotEq;
import org.xvm.asm.op.JumpEq;
import org.xvm.asm.op.JumpGt;
import org.xvm.asm.op.JumpGte;
import org.xvm.asm.op.JumpLt;
import org.xvm.asm.op.JumpLte;
import org.xvm.asm.op.JumpNotEq;
import org.xvm.asm.op.Label;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Context.Branch;

import org.xvm.util.Severity;


/**
 * Comparison binary expression.
 *
 * <ul>
 * <li><tt>COMP_EQ:    "=="</tt> - </li>
 * <li><tt>COMP_NEQ:   "!="</tt> - </li>
 * <li><tt>COMP_LT:    "<"</tt> - </li>
 * <li><tt>COMP_GT:    "><tt>"</tt> - </li>
 * <li><tt>COMP_LTEQ:  "<="</tt> - </li>
 * <li><tt>COMP_GTEQ:  ">="</tt> - </li>
 * <li><tt>COMP_ORD:   "<=><tt>"</tt> - </li>
 * </ul>
 *
 * @see TypeInfo#findEqualsFunction
 * @see TypeInfo#findCompareFunction
 * @see TypeConstant#supportsEquals
 * @see TypeConstant#supportsCompare
 * @see TypeConstant#callEquals
 * @see TypeConstant#callCompare
 */
public class CmpExpression
        extends BiExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public CmpExpression(Expression expr1, Token operator, Expression expr2)
        {
        super(expr1, operator, expr2);

        switch (operator.getId())
            {
            case COMP_EQ:
            case COMP_NEQ:
            case COMP_LT:
            case COMP_GT:
            case COMP_LTEQ:
            case COMP_GTEQ:
            case COMP_ORD:
                break;

            default:
                throw new IllegalArgumentException("operator: " + operator);
            }
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff the expression produces a Boolean value, or false iff the expression
     *         produces an Ordered value
     */
    public boolean producesBoolean()
        {
        return operator.getId() != Id.COMP_ORD;
        }

    /**
     * @return true iff the expression uses a type composition's equals() function, or false iff the
     *         expression uses a type composition's compare() function
     */
    public boolean usesEquals()
        {
        Id id = operator.getId();
        return id == Id.COMP_EQ | id == Id.COMP_NEQ;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        return producesBoolean()
                ? pool().typeBoolean()
                : pool().typeOrdered();
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        boolean fValid = true;

        // attempt to guess the types that are being compared
        TypeConstant type1 = expr1.getImplicitType(ctx);

        // allow the second expression to resolve names based on the first value type's
        // contributions
        boolean fInfer = type1 != null;
        if (fInfer)
            {
            ctx = ctx.enterInferring(type1);
            }
        TypeConstant type2 = expr2.getImplicitType(ctx);
        if (fInfer)
            {
            ctx = ctx.exit();
            }

        TypeConstant typeRequest = Op.selectCommonType(type1, type2, errs);
        Expression   expr1New    = expr1.validate(ctx, typeRequest, errs);
        if (expr1New == null)
            {
            fValid = false;
            }
        else
            {
            expr1 = expr1New;
            type1 = expr1New.getType();

            // if we weren't previously able to determine a "target" type to use, then try again now
            // that the first expression is validated
            if (typeRequest == null)
                {
                typeRequest = Op.selectCommonType(type1, type2, errs);
                }
            }

        if (fInfer)
            {
            ctx = ctx.enterInferring(type1);
            }
        Expression expr2New = expr2.validate(ctx, typeRequest, errs);
        if (fInfer)
            {
            ctx = ctx.exit();
            }

        if (expr2New == null)
            {
            fValid = false;
            }
        else
            {
            expr2 = expr2New;
            type2 = expr2New.getType();

            if (fValid)
                {
                ConstantPool pool = pool();

                // make sure that we can compare the left value to the right value
                TypeConstant typeCommon = m_typeCommon = Op.selectCommonType(type1, type2, errs);
                if (typeCommon == null)
                    {
                    // equality check for any Ref objects is allowed
                    fValid = usesEquals()
                            && type1 != null && type1.isA(pool.typeRef())
                            && type2 != null && type2.isA(pool.typeRef());
                    }
                else
                    {
                    boolean fConstant1 = expr1New.isConstant();
                    boolean fConstant2 = expr2New.isConstant();
                    fValid = usesEquals()
                            ? typeCommon.supportsEquals(pool,  type1, fConstant1) &&
                              typeCommon.supportsEquals(pool,  type2, fConstant2)
                            : typeCommon.supportsCompare(pool, type1, fConstant1) &&
                              typeCommon.supportsCompare(pool, type2, fConstant2);
                    }

                if (!fValid)
                    {
                    if (type1.equals(pool.typeNull()))
                        {
                        log(errs, Severity.ERROR, Compiler.EXPRESSION_NOT_NULLABLE,
                                type2.getValueString());
                        }
                    else if (type2.equals(pool.typeNull()))
                        {
                        log(errs, Severity.ERROR, Compiler.EXPRESSION_NOT_NULLABLE,
                                type1.getValueString());
                        }
                    else
                        {
                        log(errs, Severity.ERROR, Compiler.TYPES_NOT_COMPARABLE,
                                type1.getValueString(), type2.getValueString());
                        }
                    }
                }
            }

        TypeConstant typeResult = getImplicitType(ctx);
        Constant     constVal   = null;
        if (fValid)
            {
            if (expr1New.isConstant() && expr2New.isConstant())
                {
                try
                    {
                    constVal = expr1New.toConstant().apply(operator.getId(), expr2New.toConstant());
                    }
                catch (RuntimeException e) {}
                }
            else if (expr1New instanceof NameExpression && type2.equals(pool().typeNull()))
                {
                checkNullComparison(ctx, (NameExpression) expr1New);
                }
            else if (expr2New instanceof NameExpression && type1.equals(pool().typeNull()))
                {
                checkNullComparison(ctx, (NameExpression) expr2New);
                }
            }

        return finishValidation(typeRequired, typeResult,
                fValid ? TypeFit.Fit : TypeFit.NoFit, constVal, errs);
        }

    private void checkNullComparison(Context ctx, NameExpression exprTarget)
        {
        ConstantPool pool       = pool();
        TypeConstant typeTarget = exprTarget.getType();
        TypeConstant typeNull   = pool.typeNull();
        TypeConstant typeTrue   = null;
        TypeConstant typeFalse  = null;

        assert typeTarget.isNullable();

        switch (operator.getId())
            {
            case COMP_EQ:
                typeTrue  = typeNull;
                typeFalse = typeTarget.removeNullable(pool);
                break;

            case COMP_NEQ:
                typeTrue  = typeTarget.removeNullable(pool);
                typeFalse = typeNull;
                break;
            }

        ctx.narrowType(exprTarget, Branch.WhenTrue,  typeTrue);
        ctx.narrowType(exprTarget, Branch.WhenFalse, typeFalse);
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        if (LVal.isLocalArgument())
            {
            // evaluate the sub-expressions
            Argument arg1      = expr1.generateArgument(ctx, code, true, true, errs);
            Argument arg2      = expr2.generateArgument(ctx, code, true, true, errs);
            Argument argResult = LVal.getLocalArgument();
            OpTest   op;

            // generate the op that combines the two sub-expressions
            switch (operator.getId())
                {
                case COMP_EQ:
                    op = new IsEq(arg1, arg2, argResult);
                    break;

                case COMP_NEQ:
                    op = new IsNotEq(arg1, arg2, argResult);
                    break;

                case COMP_LT:
                    op = new IsLt(arg1, arg2, argResult);
                    break;

                case COMP_GT:
                    op = new IsGt(arg1, arg2, argResult);
                    break;

                case COMP_LTEQ:
                    op = new IsLte(arg1, arg2, argResult);
                    break;

                case COMP_GTEQ:
                    op = new IsGte(arg1, arg2, argResult);
                    break;

                case COMP_ORD:
                    op = new Cmp(arg1, arg2, argResult);
                    break;

                default:
                    throw new IllegalStateException();
                }

            op.setCommonType(m_typeCommon);
            code.add(op);
            return;
            }

        super.generateAssignment(ctx, code, LVal, errs);
        }

    @Override
    public void generateConditionalJump(
            Context ctx, Code code, Label label, boolean fWhenTrue, ErrorListener errs)
        {
        if (!isConstant() && producesBoolean())
            {
            // evaluate the sub-expressions
            Argument   arg1 = expr1.generateArgument(ctx, code, true, true, errs);
            Argument   arg2 = expr2.generateArgument(ctx, code, true, true, errs);
            OpCondJump op;

            // generate the op that combines the two sub-expressions
            switch (operator.getId())
                {
                case COMP_EQ:
                    op = fWhenTrue
                            ? new JumpEq(arg1, arg2, label)
                            : new JumpNotEq(arg1, arg2, label);
                    break;

                case COMP_NEQ:
                    op = fWhenTrue
                            ? new JumpNotEq(arg1, arg2, label)
                            : new JumpEq(arg1, arg2, label);
                    break;

                case COMP_LT:
                    op = fWhenTrue
                            ? new JumpLt(arg1, arg2, label)
                            : new JumpGte(arg1, arg2, label);
                    break;

                case COMP_GT:
                    op = fWhenTrue
                            ? new JumpGt(arg1, arg2, label)
                            : new JumpLte(arg1, arg2, label);
                    break;

                case COMP_LTEQ:
                    op = fWhenTrue
                            ? new JumpLte(arg1, arg2, label)
                            : new JumpGt(arg1, arg2, label);
                    break;

                case COMP_GTEQ:
                    op = fWhenTrue
                            ? new JumpGte(arg1, arg2, label)
                            : new JumpLt(arg1, arg2, label);
                    break;

                default:
                case COMP_ORD:
                    throw new IllegalStateException();
                }

            op.setCommonType(m_typeCommon);
            code.add(op);
            return;
            }

        super.generateConditionalJump(ctx, code, label, fWhenTrue, errs);
        }

    // ----- fields --------------------------------------------------------------------------------

    /**
     * The common type used for the comparison.
     */
    protected TypeConstant m_typeCommon;
    }
