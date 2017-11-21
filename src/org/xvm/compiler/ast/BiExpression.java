package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Constant;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;

import org.xvm.asm.Register;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.IntervalConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.GP_Add;
import org.xvm.asm.op.IsEq;
import org.xvm.asm.op.IsGt;
import org.xvm.asm.op.IsGte;
import org.xvm.asm.op.IsLt;
import org.xvm.asm.op.IsLte;
import org.xvm.asm.op.IsNotEq;
import org.xvm.asm.op.Var;
import org.xvm.compiler.Compiler;
import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.Severity;


/**
 * Generic expression for something that follows the pattern "expression operator expression".
 *
 * <ul>
 * <li><tt>COLON:      ":"</tt> - an "else" for nullability checks</li>
 * <li><tt>COND_ELSE:  "?:"</tt> - the "elvis" operator</li>
 * <li><tt>COND_OR:    "||"</tt> - </li>
 * <li><tt>COND_AND:   "&&"</tt> - </li>
 * <li><tt>BIT_OR:     "|"</tt> - </li>
 * <li><tt>BIT_XOR:    "^"</tt> - </li>
 * <li><tt>BIT_AND:    "&"</tt> - </li>
 * <li><tt>COMP_EQ:    "=="</tt> - </li>
 * <li><tt>COMP_NEQ:   "!="</tt> - </li>
 * <li><tt>COMP_LT:    "<"</tt> - </li>
 * <li><tt>COMP_GT:    "><tt>"</tt> - </li>
 * <li><tt>COMP_LTEQ:  "<="</tt> - </li>
 * <li><tt>COMP_GTEQ:  ">="</tt> - </li>
 * <li><tt>COMP_ORD:   "<=><tt>"</tt> - </li>
 * <li><tt>AS:         "as"</tt> - </li>
 * <li><tt>IS:         "is"</tt> - </li>
 * <li><tt>INSTANCEOF: "instanceof"</tt> - </li>
 * <li><tt>DOTDOT:     ".."</tt> - </li>
 * <li><tt>SHL:        "<<"</tt> - </li>
 * <li><tt>SHR:        ">><tt>"</tt> - </li>
 * <li><tt>USHR:       ">>><tt>"</tt> - </li>
 * <li><tt>ADD:        "+"</tt> - </li>
 * <li><tt>SUB:        "-"</tt> - </li>
 * <li><tt>MUL:        "*"</tt> - </li>
 * <li><tt>DIV:        "/"</tt> - </li>
 * <li><tt>MOD:        "%"</tt> - </li>
 * <li><tt>DIVMOD:     "/%"</tt> - </li>
 * </ul>
 *
 * TODO remove cut&paste:
    switch (operator.getId())
        {
        case COLON:
        case COND_ELSE:
        case COND_OR:
        case COND_AND:
        case BIT_OR:
        case BIT_XOR:
        case BIT_AND:
        case COMP_EQ:
        case COMP_NEQ:
        case COMP_LT:
        case COMP_GT:
        case COMP_LTEQ:
        case COMP_GTEQ:
        case COMP_ORD:
        case AS:
        case IS:
        case INSTANCEOF:
        case DOTDOT:
        case SHL:
        case SHR:
        case USHR:
        case ADD:
        case SUB:
        case MUL:
        case DIV:
        case MOD:
        case DIVMOD:
        }
 */
public class BiExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public BiExpression(Expression expr1, Token operator, Expression expr2)
        {
        this.expr1    = expr1;
        this.operator = operator;
        this.expr2    = expr2;
        }


    // ----- accessors -----------------------------------------------------------------------------

    @Override
    public TypeExpression toTypeExpression()
        {
        switch (operator.getId())
            {
            case ADD:
            case BIT_OR:
                return new BiTypeExpression(expr1.toTypeExpression(), operator, expr2.toTypeExpression());

            default:
                return super.toTypeExpression();
            }
        }

    @Override
    public boolean validateCondition(ErrorListener errs)
        {
        switch (operator.getId())
            {
            case BIT_AND:
            case COND_AND:
            case BIT_OR:
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
            case BIT_AND:
            case COND_AND:
                return expr1.toConditionalConstant().addAnd(expr2.toConditionalConstant());

            case BIT_OR:
            case COND_OR:
                return expr1.toConditionalConstant().addOr(expr2.toConditionalConstant());

            default:
                return super.toConditionalConstant();
            }
        }

    @Override
    public long getStartPosition()
        {
        return expr1.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return expr2.getEndPosition();
        }

    @Override
    protected Field[] getChildFields()
        {
        return CHILD_FIELDS;
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    protected boolean validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        ConstantPool pool   = pool();
        boolean      fValid = expr1.validate(ctx, , errs)
                            & expr2.validate(ctx, , errs);

        // validation of a constant expression is simpler, so do it first
        TypeConstant type1 = expr1.getImplicitType();
        TypeConstant type2 = expr2.getImplicitType();
        if (isConstant())
            {
            // first determine the type of the result, and pick a suitable default value just in
            // case everything blows up
            Constant const1 = expr1.toConstant();
            Constant const2 = expr2.toConstant();
            switch (operator.getId())
                {
                case ADD:
                case SUB:
                case MUL:
                case DIV:
                case MOD:
                case BIT_AND:
                case BIT_OR:
                case BIT_XOR:
                    {
                    TypeConstant typeResult = const1.resultType(operator.getId(), const2);
                    m_constType = typeResult;
                    // pick a default value just in case of an exception
                    m_constVal  = typeResult.isCongruentWith(type1) ? const1
                                : typeResult.isCongruentWith(type2) ? const2
                                : const1.defaultValue(typeResult);
                    }
                    break;

                case SHL:
                case SHR:
                case USHR:
                    // always use the type on the left hand side, since the numeric shifts all
                    // take Int64 as the shift amount
                    m_constType = type1;
                    m_constVal  = const1;
                    break;

                case COMP_EQ:
                case COMP_NEQ:
                case COMP_LT:
                case COMP_LTEQ:
                case COMP_GT:
                case COMP_GTEQ:
                    m_constType = pool.typeBoolean();
                    m_constVal  = pool.valFalse();
                    break;

                case COMP_ORD:
                    m_constType = pool.typeOrdered();
                    m_constVal  = pool.valEqual();
                    break;

                case DOTDOT:
                    m_constType = IntervalConstant.getIntervalTypeFor(const1);
                    m_constVal  = new IntervalConstant(pool, const1, const1);
                    break;

                case IS:
                case INSTANCEOF:
                    // left side is a value, right side is a type
                    m_constType = pool.typeBoolean();
                    m_constVal  = pool.valOf(const1.getType().isA((TypeConstant) const2));
                    return fValid;

                default:
                    operator.log(errs, getSource(), Severity.ERROR, Compiler.INVALID_OPERATION);
                    return false;
                }

            // delegate the operation to the constants
            try
                {
                m_constVal  = const1.apply(operator.getId(), const2);
                return fValid;
                }
            catch (ArithmeticException e)
                {
                log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE, m_constType,
                        getSource().toString(getStartPosition(), getEndPosition()));
                return false;
                }
            }

        // determine the type of this expression; this is even done if the sub-expressions did not
        // validate, so that compilation doesn't have to grind to a halt for just one error
        switch (operator.getId())
            {
            case COND_ELSE:
                m_constType = expr1.getImplicitType().nonNullable();
                if (fValid)
                    {
                    // the left side must be nullable, and the right expression must be assignable
                    // to the non-nullable type of the left expression, otherwise we cannot
                    // determine an "implicit type" (the error is deferred until the compilation
                    // stage, so if the type is pushed to this expression, it can use that)
                    // TODO
                    }
                break;

            case COND_OR:
            case COND_AND:
            case COMP_EQ:
            case COMP_NEQ:
            case COMP_LT:
            case COMP_GT:
            case COMP_LTEQ:
            case COMP_GTEQ:
                m_constType = pool.typeBoolean();
                if (fValid)
                    {
                    // the left side and right side types must be comparable
                    // TODO
                    }
                break;

            case IS:
            case INSTANCEOF:
                m_constType = pool.typeBoolean();
                break;

            case COMP_ORD:
                m_constType = pool.typeOrdered();
                if (fValid)
                    {
                    // the left side and right side types must be comparable
                    // TODO
                    }
                break;

            case AS:
                m_constType = expr2.toTypeExpression().ensureTypeConstant();
                break;

            case DOTDOT:
                m_constType = IntervalConstant.getIntervalTypeFor(expr1.getImplicitType());
                if (fValid)
                    {
                    // the left side and right side types must be "the same", and that type must
                    // be orderable
                    // TODO
                    }
                break;

            case DIVMOD:
                m_constType = pool.ensureParameterizedTypeConstant(pool.typeTuple(),
                        expr1.getImplicitType(), expr1.getImplicitType());
                if (fValid)
                    {
                    // find the operator on the type and determine the result of the operator
                    // TODO this is an overridable Op
                    }
                break;

            case BIT_OR:
            case BIT_XOR:
            case BIT_AND:
            case SHL:
            case SHR:
            case USHR:
            case ADD:
            case SUB:
            case MUL:
            case DIV:
            case MOD:
                if (fValid)
                    {
                    // find the operator on the type and determine the result of the operator
                    // TODO these are all overridable Op
                    }
                m_constType = expr1.getImplicitType();
                break;

            case COLON:
                // the types have to be equal, or the right expression must be assignable to the
                // type of the left expression, otherwise we cannot determine an "implicit type"
                // (the error is deferred until the compilation stage, so if the type is pushed
                // to this expression, it can use that, i.e. type inference)
                m_constType = expr1.getImplicitType();
                break;

            default:
                operator.log(errs, getSource(), Severity.ERROR, Compiler.INVALID_OPERATION);
                m_constType = expr1.getImplicitType();
                fValid = false;
                break;
            }

        return fValid;
        }

    @Override
    public int getValueCount()
        {
        // the "/%" operator results in two values
        return operator.getId() == Id.DIVMOD ? 2 : 1;
        }

    @Override
    public TypeConstant getImplicitType()
        {
        TypeConstant type = m_constType;
        assert type != null;
        return type;
        }

    @Override
    public boolean isShortCircuiting()
        {
        // with the colon operator, we know that expr1 has to be short-circuiting (or it's a
        // compiler error); all other operators are considered to be short circuiting if either
        // sub-expression is short-circuiting
        return operator.getId() == Id.COLON
                ? expr2.isShortCircuiting()
                : expr1.isShortCircuiting() || expr2.isShortCircuiting();
        }

    @Override
    public boolean isCompletable()
        {
        switch (operator.getId())
            {
            case COLON:
            case COND_ELSE:
            case COND_OR:
            case COND_AND:
                // these can complete if the first expression can complete, because the result can
                // be calculated from the first expression, depending on what its answer is
                return expr1.isCompletable();

            default:
                // these can only complete if both sub-expressions can complete
                return expr1.isCompletable() && expr2.isCompletable();
            }
        }

    @Override
    public boolean isConstant()
        {
        // it may have already been calculated by validate()
        if (m_constType != null)
            {
            return m_constVal != null;
            }

        // just to reduce complexity, several expression types are not regarded as constant; these
        // limitations may be relaxed in the future
        switch (operator.getId())
            {
            case COLON:
            case COND_ELSE:
            case AS:
            case DIVMOD:
                return false;
            }

        if (expr1.isConstant() && expr2.isConstant())
            {
            return true;
            }

        if (expr1.isConstant())
            {
            switch (operator.getId())
                {
                case COLON:
                    // if the thing on the left of the colon evaluates to a constant value, then
                    // that is the result
                    return true;

                case COND_ELSE:
                    // as long as the thing on the left of the colon is not null, then the result
                    // is the thing on the left of the colon, which is constant
                    return !expr1.isConstantNull();

                case COND_OR:
                    // short circuit logic
                    return expr1.isConstantTrue();

                case COND_AND:
                    // short circuit logic
                    return expr1.isConstantFalse();

                default:
                    // - each of these could have side-effects from the right hand side, so they are
                    //   not considered to be constant, even if we know what the resulting value is
                    // - or they are comparisons, so we'd have to know both the left AND right hand
                    //   side values
                    // - or interval/range can't be constant if we don't know the upper limit
                    return false;
                }
            }

        return false;
        }

    @Override
    public Constant toConstant()
        {
        assert isConstant();        // can we prove that it is a constant?
        assert m_constVal != null;  // what happens if this is called before validation?
        return m_constVal;
        }

    @Override
    public Argument generateArgument(Code code, TypeConstant type, boolean fTupleOk, ErrorListener errs)
        {
        if (!isConstant())
            {
            switch (operator.getId())
                {
                case COLON:
                    // TODO
                    throw new UnsupportedOperationException();

                case COND_ELSE:
                    // TODO
                    throw new UnsupportedOperationException();

                case AS:
                    // TODO
                    throw new UnsupportedOperationException();

                case IS:
                    // TODO
                    throw new UnsupportedOperationException();

                case INSTANCEOF:
                    // TODO
                    throw new UnsupportedOperationException();

                case DOTDOT:
                    // TODO
                    throw new UnsupportedOperationException();

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
                case COMP_EQ:
                case COMP_NEQ:
                case COMP_LT:
                case COMP_GT:
                case COMP_LTEQ:
                case COMP_GTEQ:
                case COMP_ORD:
                    code.add(new Var(m_constType));
                    Register regResult = code.lastRegister();
                    generateAssignment(code, new Assignable(regResult), errs);
                    return regResult;

                case DIVMOD:
                    // TODO
                    throw new UnsupportedOperationException();
                }
            }

        return super.generateArgument(code, type, fTupleOk, errs);
        }

    @Override
    public Argument[] generateArguments(Code code, TypeConstant[] atype, boolean fTupleOk, ErrorListener errs)
        {
        if (operator.getId() == Id.DIVMOD && atype.length == 2)
            {
            // TODO
            throw new UnsupportedOperationException();
            }

        return super.generateArguments(code, atype, fTupleOk, errs);
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        if (LVal.isLocalArgument())
            {
            // determine the types to request from the sub-expressions
            // TODO right now just use their implicit types, but for type inference to work, this should be based on the type that we're asked for
            assert LVal.getType().isCongruentWith(m_constType) || m_constType.isA(LVal.getType());
            TypeConstant type1 = expr1.getImplicitType();
            TypeConstant type2 = expr2.getImplicitType();

            // evaluate the sub-expressions
            // TODO if (LVal.isTemp() && LVal.getType().equals(type1)) -> genAssign, IP_ADD
            Argument arg1 = expr1.generateArgument(code, type1, false, errs);
            Argument arg2 = expr2.generateArgument(code, type2, false, errs);

            // generate the op that combines the two sub-expressions
            switch (operator.getId())
                {
                case COLON:
                    // TODO
                    throw new UnsupportedOperationException();

                case COND_ELSE:
                    // TODO
                    throw new UnsupportedOperationException();

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

                case AS:
                    // TODO
                    throw new UnsupportedOperationException();

                case IS:
                    // TODO
                    throw new UnsupportedOperationException();

                case INSTANCEOF:
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
                    // TODO
                    throw new UnsupportedOperationException();

                case MUL:
                    // TODO
                    throw new UnsupportedOperationException();

                case DIVMOD:
                    if (LVal.getType().isTuple())
                        {
                        // TODO
                        throw new UnsupportedOperationException();
                        }
                    // fall through
                case DIV:
                    // TODO
                    throw new UnsupportedOperationException();

                case MOD:
                    // TODO
                    throw new UnsupportedOperationException();
                }

            return;
            }

        super.generateAssignment(code, LVal, errs);
        }

    @Override
    public void generateAssignments(Code code, Assignable[] aLVal, ErrorListener errs)
        {
        if (operator.getId() == Id.DIVMOD && aLVal.length == 2)
            {
            // TODO
            throw new UnsupportedOperationException();
            }

        super.generateAssignments(code, aLVal, errs);
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(expr1)
          .append(' ')
          .append(operator.getId().TEXT)
          .append(' ')
          .append(expr2);

        return sb.toString();
        }

    @Override
        public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Expression expr1;
    protected Token      operator;
    protected Expression expr2;

    /**
     * Cached type, post-validate.
     */
    private TypeConstant m_constType;
    /**
     * Cached constant, post-validate.
     */
    private Constant m_constVal;

    private static final Field[] CHILD_FIELDS =
            fieldsForNames(BiExpression.class, "expr1", "expr2");
    }
