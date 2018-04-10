package org.xvm.compiler.ast;


import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Register;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;
import org.xvm.asm.op.GP_Add;
import org.xvm.asm.op.GP_Div;
import org.xvm.asm.op.GP_Mod;
import org.xvm.asm.op.GP_Mul;
import org.xvm.asm.op.GP_Sub;
import org.xvm.asm.op.Var;
import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;
import org.xvm.compiler.ast.Statement.Context;
import org.xvm.util.Severity;


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

    @Override
    public boolean validateCondition(ErrorListener errs)
        {
        switch (operator.getId())
            {
            case COND_AND:
            case COND_OR:
                return expr1.validateCondition(errs) && expr2.validateCondition(errs);

            default:
                return super.validateCondition(errs);
            }
        }

    @Override
    public ConditionalConstant toConditionalConstant()
        {
        switch (operator.getId())
            {
            case COND_AND:
                return expr1.toConditionalConstant().addAnd(expr2.toConditionalConstant());

            case COND_OR:
                return expr1.toConditionalConstant().addOr(expr2.toConditionalConstant());

            default:
                return super.toConditionalConstant();
            }
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

    private boolean doesTypeProduce(TypeConstant typeLeft, TypeConstant typeResult)
        {
        if (expr1.testFit(ctx, typeRequired, TuplePref.Rejected).isFit())
        }

    private Set

    @Override
    public boolean isAborting()
        {
        switch (operator.getId())
            {
            case COND_OR:
            case COND_AND:
                // these can complete if the first expression can complete, because the result can
                // be calculated from the first expression, depending on what its answer is; thus
                // the expression aborts if the first of the two expressions aborts
                return expr1.isAborting();

            default:
                // these can only complete if both sub-expressions can complete
                return expr1.isAborting() || expr2.isAborting();
            }
        }

    @Override
    public Argument generateArgument(Code code, boolean fPack, ErrorListener errs)
        {
        if (!isConstant())
            {
            switch (operator.getId())
                {
                case DIVMOD:
                    if (!isSingle())
                        {
                        // TODO
                        throw new UnsupportedOperationException();
                        }
                case DOTDOT:
                case ADD:
                case SUB:
                case MUL:
                case DIV:
                case MOD:
                case COND_OR:
                case COND_AND:
                case BIT_OR:
                case BIT_XOR:
                case BIT_AND:
                case SHL:
                case SHR:
                case USHR:
                    code.add(new Var(getType()));
                    Register regResult = code.lastRegister();
                    generateAssignment(code, new Assignable(regResult), errs);
                    return regResult;
                }
            }

        return super.generateArgument(code, fPack, errs);
        }

    @Override
    public Argument[] generateArguments(Code code, boolean fPack, ErrorListener errs)
        {
        if (getValueCount() == 2)
            {
            assert operator.getId() == Id.DIVMOD;
            // TODO
            throw new UnsupportedOperationException();
            }

        return super.generateArguments(code, fPack, errs);
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
                case COND_OR:
                    // TODO
                    throw new UnsupportedOperationException();

                case COND_AND:
                    // TODO
                    throw new UnsupportedOperationException();

                case BIT_OR:
                    // TODO
                    throw new UnsupportedOperationException();

                case BIT_XOR:
                    // TODO
                    throw new UnsupportedOperationException();

                case BIT_AND:
                    // TODO
                    throw new UnsupportedOperationException();

                case DOTDOT:
                    // TODO
                    throw new UnsupportedOperationException();

                case SHL:
                    // TODO
                    throw new UnsupportedOperationException();

                case SHR:
                    // TODO
                    throw new UnsupportedOperationException();

                case USHR:
                    // TODO
                    throw new UnsupportedOperationException();

                case ADD:
                    code.add(new GP_Add(arg1, arg2, LVal.getLocalArgument()));
                    break;

                case SUB:
                    code.add(new GP_Sub(arg1, arg2, LVal.getLocalArgument()));
                    break;

                case MUL:
                    code.add(new GP_Mul(arg1, arg2, LVal.getLocalArgument()));
                    break;

                case DIVMOD:
                    if (LVal.getType().isTuple())
                        {
                        // TODO
                        throw new UnsupportedOperationException();
                        }
                    // fall through
                case DIV:
                    code.add(new GP_Div(arg1, arg2, LVal.getLocalArgument()));
                    break;

                case MOD:
                    code.add(new GP_Mod(arg1, arg2, LVal.getLocalArgument()));
                    break;
                }

            return;
            }

        super.generateAssignment(code, LVal, errs);
        }

    @Override
    public void generateAssignments(Code code, Assignable[] aLVal, ErrorListener errs)
        {
        if (getValueCount() == 2)
            {
            assert operator.getId() == Id.DIVMOD;
            // TODO
            throw new UnsupportedOperationException();
            }

        super.generateAssignments(code, aLVal, errs);
        }


    // ----- helpers -------------------------------------------------------------------------------

    public String getDefaultMethodName()
        {
        switch (operator.getId())
            {
            case BIT_AND:
            case COND_AND:      // it uses the same operator method, but the compiler short-circuits  TODO move to ShortCircuitingBooleanRelOpExpression
                return "and";

            case BIT_OR:
            case COND_OR:       // it uses the same operator method, but the compiler short-circuits  TODO move to ShortCircuitingBooleanRelOpExpression
                return "or";

            case BIT_XOR:
                return "xor";

            case DOTDOT:
                return "through";

            case SHL:
                return "shiftLeft";

            case SHR:
                return "shiftRight";

            case USHR:
                return "shiftAllRight";

            case ADD:
                return "add";

            case SUB:
                return "sub";

            case MUL:
                return "mul";

            case DIV:
                return "div";

            case MOD:
                return "mod";

            case DIVMOD:
                return "divmod";

            default:
                throw new IllegalStateException();
            }
        }

    public String getOperatorString()
        {
        return operator.getId().TEXT;
        }


    // ----- fields --------------------------------------------------------------------------------

    }
