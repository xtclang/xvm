package org.xvm.compiler.ast;


import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Var;

import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Statement.Context;


/**
 * Conditional operator expressions "||" and "&&".
 *
 * <ul>
 * <li><tt>COND_OR:    "||"</tt> - </li>
 * <li><tt>COND_AND:   "&&"</tt> - </li>
 * </ul>
 */
public class CondOpExpression
        extends BiExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    public CondOpExpression(Expression expr1, Token operator, Expression expr2)
        {
        super(expr1, operator, expr2);

        switch (operator.getId())
            {
            case COND_OR:
            case COND_AND:
                break;

            default:
                throw new IllegalArgumentException("operator: " + operator);
            }
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff the expression is a conditional "and" expression
     */
    public boolean isAnd()
        {
        return operator.getId() == Id.COND_AND;
        }

    /**
     * @return true iff the expression is a conditional "or" expression
     */
    public boolean isOr()
        {
        return operator.getId() == Id.COND_OR;
        }

    @Override
    public boolean validateCondition(ErrorListener errs)
        {
        return expr1.validateCondition(errs) && expr2.validateCondition(errs);
        }

    @Override
    public ConditionalConstant toConditionalConstant()
        {
        return isAnd()
                ? expr1.toConditionalConstant().addAnd(expr2.toConditionalConstant())
                : expr1.toConditionalConstant().addOr (expr2.toConditionalConstant());
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType()
        {
        return pool().typeBoolean();
        }

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, TuplePref pref)
        {
        // TODO what about @Auto? need a simple way to tack on a conversion check to each expression type
        return pool().typeBoolean().isA(typeRequired)
                ? TypeFit.Fit
                : TypeFit.NoFit;
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, TuplePref pref, ErrorListener errs)
        {
        boolean fValid = true;

        return fValid
                ? this
                : null;
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
    public Argument generateArgument(Code code, boolean fPack, ErrorListener errs)
        {
        if (!isConstant())
            {
            // REVIEW
            code.add(new Var(getType()));
            Register regResult = code.lastRegister();
            generateAssignment(code, new Assignable(regResult), errs);
            return regResult;
            }

        return super.generateArgument(code, fPack, errs);
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        if (LVal.isLocalArgument()) // REVIEW what other options are there?
            {
            // evaluate the sub-expressions
            Argument arg1 = expr1.generateArgument(code, false, errs);
            Argument arg2 = expr2.generateArgument(code, false, errs);

            // generate the op that combines the two sub-expressions
            if (isAnd())
                {
                // TODO
                throw new UnsupportedOperationException();
                }
            else
                {
                // TODO
                throw new UnsupportedOperationException();
                }
            }
        else
            {
            super.generateAssignment(code, LVal, errs);
            }
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return the default name of the "@Op" method
     */
    public String getDefaultMethodName()
        {
        // it uses the same operator method name as the non-conditional operator, but this
        // expression type short-circuits
        return isAnd()
                ? "and"
                : "or";
        }

    /**
     * @return the default operator symbol for the "@Op" method
     */
    public String getOperatorString()
        {
        // it uses the same operator method name as the non-conditional operator, but this
        // expression type short-circuits
        return isAnd()
                ? "&"
                : "|";
        }


    // ----- fields --------------------------------------------------------------------------------

    }
