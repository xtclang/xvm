package org.xvm.compiler.ast;


import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.IsEq;
import org.xvm.asm.op.IsGt;
import org.xvm.asm.op.IsGte;
import org.xvm.asm.op.IsLt;
import org.xvm.asm.op.IsLte;
import org.xvm.asm.op.IsNotEq;
import org.xvm.asm.op.Var;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;

import org.xvm.compiler.ast.Statement.Context;

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
 * TODO remove cut&paste:
    switch (operator.getId())
        {
        case COMP_EQ:
        case COMP_NEQ:
        case COMP_LT:
        case COMP_GT:
        case COMP_LTEQ:
        case COMP_GTEQ:
        case COMP_ORD:
        }
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


    // ----- compilation ---------------------------------------------------------------------------


    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, TuplePref pref, ErrorListener errs)
        {
        ConstantPool pool   = pool();
        boolean      fValid = true;

        // expr1.validate(ctx, null, errs);
        // fValid &= expr2.validate(ctx, null, errs);      // TODO need a type here

//        // validation of a constant expression is simpler, so do it first
//        TypeConstant type1 = expr1.getType();
//        TypeConstant type2 = expr2.getType();
//        if (isConstant())
//            {
//            // first determine the type of the result, and pick a suitable default value just in
//            // case everything blows up
//            Constant const1 = expr1.toConstant();
//            Constant const2 = expr2.toConstant();
//            switch (operator.getId())
//                {
//                case COMP_EQ:
//                case COMP_NEQ:
//                case COMP_LT:
//                case COMP_LTEQ:
//                case COMP_GT:
//                case COMP_GTEQ:
//                    m_constType = pool.typeBoolean();
//                    m_constVal  = pool.valFalse();
//                    break;
//
//                case COMP_ORD:
//                    m_constType = pool.typeOrdered();
//                    m_constVal  = pool.valEqual();
//                    break;
//
//                default:
//                    operator.log(errs, getSource(), Severity.ERROR, Compiler.INVALID_OPERATION);
//                    return false;
//                }
//
//            // delegate the operation to the constants
//            try
//                {
//                m_constVal  = const1.apply(operator.getId(), const2);
//                return fValid;
//                }
//            catch (ArithmeticException e)
//                {
//                log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE, m_constType,
//                        getSource().toString(getStartPosition(), getEndPosition()));
//                return false;
//                }
//            }

        // determine the type of this expression; this is even done if the sub-expressions did not
        // validate, so that compilation doesn't have to grind to a halt for just one error
        switch (operator.getId())
            {
            case COMP_EQ:
            case COMP_NEQ:
            case COMP_LT:
            case COMP_GT:
            case COMP_LTEQ:
            case COMP_GTEQ:
                // TODO pool.typeBoolean();
                break;

            case COMP_ORD:
                // TODO pool.typeOrdered();
                break;

            default:
                operator.log(errs, getSource(), Severity.ERROR, Compiler.INVALID_OPERATION);
                // TODO assume required type or expr1.getType();
                fValid = false;
                break;
            }

        return fValid
                ? this
                : null;
        }

    @Override
    public Argument generateArgument(Code code, boolean fPack, ErrorListener errs)
        {
        if (!isConstant())
            {
            switch (operator.getId())
                {
                case COMP_EQ:
                case COMP_NEQ:
                case COMP_LT:
                case COMP_GT:
                case COMP_LTEQ:
                case COMP_GTEQ:
                case COMP_ORD:
                    code.add(new Var(getType()));
                    Register regResult = code.lastRegister();
                    generateAssignment(code, new Assignable(regResult), errs);
                    return regResult;
                }
            }

        return super.generateArgument(code, fPack, errs);
        }


    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        if (LVal.isLocalArgument())
            {
            // evaluate the sub-expressions
            Argument arg1 = expr1.generateArgument(code, false, errs);
            Argument arg2 = expr2.generateArgument(code, false, errs);

            // generate the op that combines the two sub-expressions
            switch (operator.getId())
                {
                case COMP_EQ:
                    code.add(new IsEq(arg1, arg2, LVal.getLocalArgument()));
                    break;

                case COMP_NEQ:
                    code.add(new IsNotEq(arg1, arg2, LVal.getLocalArgument()));
                    break;

                case COMP_LT:
                    code.add(new IsLt(arg1, arg2, LVal.getLocalArgument()));
                    break;

                case COMP_GT:
                    code.add(new IsGt(arg1, arg2, LVal.getLocalArgument()));
                    break;

                case COMP_LTEQ:
                    code.add(new IsLte(arg1, arg2, LVal.getLocalArgument()));
                    break;

                case COMP_GTEQ:
                    code.add(new IsGte(arg1, arg2, LVal.getLocalArgument()));
                    break;

                case COMP_ORD:
                    // TODO
                    throw new UnsupportedOperationException();
                }

            return;
            }

        super.generateAssignment(code, LVal, errs);
        }


    // ----- fields --------------------------------------------------------------------------------

    }
