package org.xvm.compiler.ast;


import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Argument;

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

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Statement.Context;


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
        ConstantPool pool = pool();
        boolean fValid = true;

        Expression expr1New = expr1.validate(ctx, null, errs);
        TypeConstant type1 = null;
        if (expr1New == null)
            {
            fValid = false;
            }
        else
            {
            expr1 = expr1New;
            type1 = expr1New.getType();
            fValid &= expr1New.getTypeFit().isFit();
            }

        // allow the second expression to resolve names based on the first value type's
        // contributions
        Context ctx2 = type1 == null
                ? ctx
                : ctx.createInferringContext(type1);

        Expression expr2New = expr2.validate(ctx2, type1, errs);
        TypeConstant type2 = null;
        if (expr2New == null)
            {
            fValid = false;
            }
        else
            {
            expr2 = expr2New;
            type2 = expr2New.getType();
            fValid &= expr2New.getTypeFit().isFit();
            }

        if (fValid)
            {
            fValid &= usesEquals()
                    ? type1.supportsEquals(type2, expr2New.hasConstantValue(), errs)
                    : type1.supportsCompare(type2, expr2New.hasConstantValue(), errs);
            }

        if (!fValid)
            {
            return finishValidation(typeRequired, getImplicitType(ctx), TypeFit.NoFit, null, errs);
            }

        TypeConstant typeResult = getImplicitType(ctx);
        Constant     constVal   = null;
        if (expr1New.hasConstantValue() && expr2.hasConstantValue())
            {
            try
                {
                constVal = expr1New.toConstant().apply(operator.getId(), expr2New.toConstant());
                }
            catch (RuntimeException e)
                {
                // it's an error, but pick some arbitrary default
                fValid = false;
                constVal = producesBoolean()
                        ? pool.valFalse()
                        : pool.valEqual();
                }
            }

        return finishValidation(typeRequired, typeResult, fValid ? TypeFit.Fit : TypeFit.NoFit,
                constVal, errs);
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        if (LVal.isLocalArgument())
            {
            // evaluate the sub-expressions
            Argument arg1      = expr1.generateArgument(code, true, true, errs);
            Argument arg2      = expr2.generateArgument(code, true, true, errs);
            Argument argResult = LVal.getLocalArgument();

            // generate the op that combines the two sub-expressions
            switch (operator.getId())
                {
                case COMP_EQ:
                    code.add(new IsEq(arg1, arg2, argResult));
                    break;

                case COMP_NEQ:
                    code.add(new IsNotEq(arg1, arg2, argResult));
                    break;

                case COMP_LT:
                    code.add(new IsLt(arg1, arg2, argResult));
                    break;

                case COMP_GT:
                    code.add(new IsGt(arg1, arg2, argResult));
                    break;

                case COMP_LTEQ:
                    code.add(new IsLte(arg1, arg2, argResult));
                    break;

                case COMP_GTEQ:
                    code.add(new IsGte(arg1, arg2, argResult));
                    break;

                case COMP_ORD:
                    code.add(new Cmp(arg1, arg2, argResult));
                }

            return;
            }

        super.generateAssignment(code, LVal, errs);
        }

    @Override
    public void generateConditionalJump(Code code, Label label, boolean fWhenTrue, ErrorListener errs)
        {
        if (!hasConstantValue() && producesBoolean())
            {
            // evaluate the sub-expressions
            Argument arg1 = expr1.generateArgument(code, true, true, errs);
            Argument arg2 = expr2.generateArgument(code, true, true, errs);

            // generate the op that combines the two sub-expressions
            switch (operator.getId())
                {
                case COMP_EQ:
                    code.add(new JumpEq(arg1, arg2, label));
                    return;

                case COMP_NEQ:
                    code.add(new JumpNotEq(arg1, arg2, label));
                    return;

                case COMP_LT:
                    code.add(new JumpLt(arg1, arg2, label));
                    return;

                case COMP_GT:
                    code.add(new JumpGt(arg1, arg2, label));
                    return;

                case COMP_LTEQ:
                    code.add(new JumpLte(arg1, arg2, label));
                    return;

                case COMP_GTEQ:
                    code.add(new JumpGte(arg1, arg2, label));
                    return;

                default:
                case COMP_ORD:
                    throw new IllegalStateException();
                }
            }

        super.generateConditionalJump(code, label, fWhenTrue, errs);
        }

    // ----- fields --------------------------------------------------------------------------------

    }
