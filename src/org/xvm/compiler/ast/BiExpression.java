package org.xvm.compiler.ast;


import java.lang.reflect.Field;

import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;

import org.xvm.asm.constants.CharConstant;
import org.xvm.asm.constants.ConditionalConstant;
import org.xvm.asm.constants.Int8Constant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.LiteralConstant;
import org.xvm.asm.constants.StringConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.constants.UInt8Constant;
import org.xvm.compiler.Compiler;
import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Statement.Context;

import org.xvm.util.PackedInteger;
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
    protected boolean validate(Context ctx, ErrorListener errs)
        {
        boolean fValid = expr1.validate(ctx, errs)
                       & expr2.validate(ctx, errs);

        // only bother checking the operation itself and calculating the "real" implicit type if
        // the sub-expressions did not have any errors
        if (fValid)
            {
            ConstantPool pool  = pool();
            TypeConstant type1 = expr1.getImplicitType();
            TypeConstant type2 = expr2.getImplicitType();

            switch (operator.getId())
                {
                case COLON:
                    // the types have to be equal, or the right expression must be assignable to the
                    // type of the left expression, otherwise we cannot determine an "implicit type"
                    // (the error is deferred until the compilation stage, so if the type is pushed
                    // to this expression, it can use that, i.e. type inference)
                    notImplemented(); // TODO
                    break;

                case COND_ELSE:
                    // the left side must be nullable, and the right expression must be assignable
                    // to the non-nullable type of the left expression, otherwise we cannot
                    // determine an "implicit type" (the error is deferred until the compilation
                    // stage, so if the type is pushed to this expression, it can use that)
                    notImplemented(); // TODO
                    break;

                case COND_OR:
                case COND_AND:
                case BIT_OR:
                case BIT_XOR:
                case BIT_AND:
                case SHL:
                case SHR:
                case USHR:
                    throw notImplemented(); // TODO

                case ADD:
                    if (isConstant())
                        {
                        // constant types that can be added: Int/FP (both lits and vals), FP,
                        // String, Char, Type
                        if (type1.equals(type2))
                            {
                            switch (type1.getEcstasyClassName())
                                {
                                case "String":
                                    m_constType = type1;
                                    m_constVal  = ((StringConstant) expr1.toConstant())
                                            .add((StringConstant) expr2.toConstant());
                                    break;

                                case "Char":
                                    m_constType = pool.typeString();
                                    m_constVal  = ((CharConstant) expr1.toConstant())
                                            .add((CharConstant) expr2.toConstant());
                                    break;

                                case "FPLiteral":
                                case "IntLiteral":
                                    m_constType = type1;
                                    m_constVal  = ((LiteralConstant) expr1.toConstant())
                                            .add((LiteralConstant) expr2.toConstant());
                                    break;

                                case "Int8":
                                    m_constType = type1;
                                    try
                                        {
                                        m_constVal = ((Int8Constant) expr1.toConstant())
                                                .add((Int8Constant) expr2.toConstant());
                                        }
                                    catch (ArithmeticException e)
                                        {
                                        fValid = false;
                                        log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE, type1,
                                                getSource().toString(getStartPosition(), getEndPosition()));

                                        // use a zero value, since it's an error anyways
                                        m_constVal = pool.ensureInt8Constant(0);
                                        }
                                    break;

                                case "UInt8":
                                    m_constType = type1;
                                    try
                                        {
                                        m_constVal = ((UInt8Constant) expr1.toConstant())
                                                .add((UInt8Constant) expr2.toConstant());
                                        }
                                    catch (ArithmeticException e)
                                        {
                                        fValid = false;
                                        log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE, type1,
                                                getSource().toString(getStartPosition(), getEndPosition()));

                                        // use a zero value, since it's an error anyways
                                        m_constVal = pool.ensureUInt8Constant(0);
                                        }
                                    break;

                                case "Int16":
                                case "Int32":
                                case "Int64":
                                case "Int128":
                                case "VarInt":
                                case "UInt16":
                                case "UInt32":
                                case "UInt64":
                                case "UInt128":
                                case "VarUInt":
                                    m_constType = type1;
                                    try
                                        {
                                        m_constVal = ((IntConstant) expr1.toConstant())
                                                .add((IntConstant) expr2.toConstant());
                                        }
                                    catch (ArithmeticException e)
                                        {
                                        fValid = false;
                                        log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE, type1,
                                                getSource().toString(getStartPosition(), getEndPosition()));

                                        // use a zero value, since it's an error anyways
                                        m_constVal = pool.ensureIntConstant(PackedInteger.ZERO,
                                                expr1.toConstant().getFormat());
                                        }
                                    break;

                                // TODO case "type + type"

                                default:
                                    fValid = false;
                                    log(errs, Severity.ERROR, Compiler.INVALID_OPERATION);
                                }
                            }
                        else
                            {
                            String sConvertTo = type2.getEcstasyClassName();
                            switch (type1.getEcstasyClassName())
                                {
                                case "String":
                                    // has to be a Char being added to it
                                    if (sConvertTo.equals("Char"))
                                        {
                                        m_constType = type1;
                                        m_constVal = ((StringConstant) expr1.toConstant())
                                                .add((StringConstant) expr2.toConstant());
                                        }
                                    break;

                                case "Char":
                                    if (type2.getEcstasyClassName().equals("String"))
                                    m_constType = pool.typeString();
                                    m_constVal  = ((CharConstant) expr1.toConstant())
                                            .add((CharConstant) expr2.toConstant());
                                    break;

                                case "FPLiteral":
                                case "IntLiteral":
                                    m_constType = type1;
                                    m_constVal  = ((LiteralConstant) expr1.toConstant())
                                            .add((LiteralConstant) expr2.toConstant());
                                    break;

                                case "Int8":
                                    m_constType = type1;
                                    try
                                        {
                                        m_constVal = ((Int8Constant) expr1.toConstant())
                                                .add((Int8Constant) expr2.toConstant());
                                        }
                                    catch (ArithmeticException e)
                                        {
                                        fValid = false;
                                        log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE, type1,
                                                getSource().toString(getStartPosition(), getEndPosition()));

                                        // use a zero value, since it's an error anyways
                                        m_constVal = pool.ensureInt8Constant(0);
                                        }
                                    break;

                                case "UInt8":
                                    m_constType = type1;
                                    try
                                        {
                                        m_constVal = ((UInt8Constant) expr1.toConstant())
                                                .add((UInt8Constant) expr2.toConstant());
                                        }
                                    catch (ArithmeticException e)
                                        {
                                        fValid = false;
                                        log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE, type1,
                                                getSource().toString(getStartPosition(), getEndPosition()));

                                        // use a zero value, since it's an error anyways
                                        m_constVal = pool.ensureUInt8Constant(0);
                                        }
                                    break;

                                case "Int16":
                                case "Int32":
                                case "Int64":
                                case "Int128":
                                case "VarInt":
                                case "UInt16":
                                case "UInt32":
                                case "UInt64":
                                case "UInt128":
                                case "VarUInt":
                                    m_constType = type1;
                                    try
                                        {
                                        m_constVal = ((IntConstant) expr1.toConstant())
                                                .add((IntConstant) expr2.toConstant());
                                        }
                                    catch (ArithmeticException e)
                                        {
                                        fValid = false;
                                        log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE, type1,
                                                getSource().toString(getStartPosition(), getEndPosition()));

                                        // use a zero value, since it's an error anyways
                                        m_constVal = pool.ensureIntConstant(PackedInteger.ZERO,
                                                expr1.toConstant().getFormat());
                                        }
                                    break;

                                // TODO case "type + type"
                                // - type<t1> + type<t2> = type<t1+t2>

                                }

                            if (fValid && m_constVal == null)
                                {
                                fValid = false;
                                log(errs, Severity.ERROR, Compiler.INVALID_OPERATION);
                                }

                            // otherwise:
                            // - char + string                  = string
                            // - string + char                  = string
                            // - some int type + intliteral     = same int type
                            // - intliteral + some int type     = same int type
                            // - some fp type + intliteral      = same fp type
                            // - some fp type + fpliteral       = same fp type
                            // - intliteral + some fp type      = same fp type
                            // - fpliteral  + some fp type      = same fp type
                            // - intliteral + fpliteral         = fpliteral
                            // - fpliteral + intliteral         = fpliteral
                            }
                        }
                    else
                        {
                        // TODO - not constant
                        }

                case SUB:
                case MUL:
                case DIV:
                case MOD:
                case DIVMOD:
                    // left-associative operators require that the operator (@Op <name>) be present
                    // on the type of the left side expression.
                    notImplemented(); // TODO
                    break;

                case COMP_EQ:
                case COMP_NEQ:
                case COMP_LT:
                case COMP_GT:
                case COMP_LTEQ:
                case COMP_GTEQ:
                case COMP_ORD:
                    // comparison operators require the types to be the same
                    notImplemented(); // TODO
                    break;

                case AS:
                case IS:
                case INSTANCEOF:
                case DOTDOT:

                default:
                    operator.log(errs, getSource(), Severity.ERROR, Compiler.FATAL_ERROR);
                    fValid = false;
                    break;
                }
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
        switch (operator.getId())
            {
            case COMP_EQ:
            case COMP_NEQ:
            case COMP_LT:
            case COMP_GT:
            case COMP_LTEQ:
            case COMP_GTEQ:
            case AS:
            case IS:
            case INSTANCEOF:
                return pool().typeBoolean();

            case COMP_ORD:
                return pool().typeOrdered();

            case COLON:
                return expr1.getImplicitType();

            case COND_ELSE:
                {
                // TODO a?.b?.c : d
                return expr1.getImplicitType().nonNullable();
                }

            case DOTDOT:
                // Sequential: Range<expr1.getImplicitType()>
                // Orderable: Interval<expr1.getImplicitType()>
                // otherwise: will be detected as compiler error by validate(), so assume Orderable
                {
                ConstantPool pool = pool();
                TypeConstant typeElement  = expr1.getImplicitType();
                TypeConstant typeInterval = typeElement.isA(pool.typeSequential())
                        ? pool.typeRange()
                        : pool.typeInterval();
                return pool.ensureParameterizedTypeConstant(typeInterval,
                        new TypeConstant[] {typeElement});
                }

            case COND_OR:
            case COND_AND:
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
                // TODO examine expr1's type's methods for @Op's and determine result type from that
                return expr1.getImplicitType();

            case DIVMOD:
            default:
                throw new IllegalStateException(operator.toString());
            }
        }

    @Override
    public TypeConstant[] getImplicitTypes()
        {
        if (operator.getId() == Id.DIVMOD)
            {
            // TODO examine expr1's type's methods for @Op's and determine result types from that
            TypeConstant type = expr1.getImplicitType();
            return new TypeConstant[] {type, type};
            }

        return super.getImplicitTypes();
        }

    @Override
    public boolean isConstant()
        {
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
    public Constant generateConstant(Code code, TypeConstant type, ErrorListener errs)
        {
        if (isConstant())
            {
            ConstantPool pool = pool();
            switch (operator.getId())
                {
                case COLON:
                    // TODO
                    throw new UnsupportedOperationException();

                case COND_ELSE:
                    return (expr1.isConstantNull() ? expr2 : expr1).generateConstant(code, type, errs);

                case BIT_OR:
                    if (type.equals(pool.typeIntLiteral()))
                        {
                        Argument arg1 = expr1.generateConstant(code, type, errs);
                        Argument arg2 = expr1.generateConstant(code, type, errs);
                        if (arg1 instanceof LiteralConstant && arg2 instanceof LiteralConstant)
                            {
                            PackedInteger pi1      = ((LiteralConstant) arg1).getIntegerValue();
                            PackedInteger pi2      = ((LiteralConstant) arg2).getIntegerValue();
                            int           radix    = ((LiteralConstant) arg1).getIntegerRadix();
                            PackedInteger piResult = pi1.isBig() || pi2.isBig()
                                    ? new PackedInteger(pi1.getBigInteger().or(pi2.getBigInteger()))
                                    : PackedInteger.valueOf(pi1.getLong() | pi2.getLong());
                            return pool.ensureLiteralConstant(Format.IntLiteral, piResult.toString(radix));
                            }
                        }
                    else if (type.equals(pool.typeInt()))
                        {
                        Argument arg1 = expr1.generateConstant(code, type, errs);
                        Argument arg2 = expr1.generateConstant(code, type, errs);
                        if (arg1 instanceof LiteralConstant && arg2 instanceof LiteralConstant)
                            {
                            PackedInteger pi1      = ((LiteralConstant) arg1).getIntegerValue();
                            PackedInteger pi2      = ((LiteralConstant) arg2).getIntegerValue();
                            int           radix    = ((LiteralConstant) arg1).getIntegerRadix();
                            PackedInteger piResult = pi1.isBig() || pi2.isBig()
                                    ? new PackedInteger(pi1.getBigInteger().or(pi2.getBigInteger()))
                                    : PackedInteger.valueOf(pi1.getLong() | pi2.getLong());
                            return pool.ensureLiteralConstant(Format.IntLiteral, piResult.toString(radix));
                            }
                        }
                    // else if (...) TODO Int and UInt 8-128 and Var length
                    // else if (constType.equals())     TODO type | type

                    // fall through for logical boolean "or"
                case COND_OR:
                    if (type.equals(pool.typeBoolean()))
                        {
                        // if the first expression is a boolean true, then the result is a boolean
                        // true;  otherwise if the second expression is a boolean true, then the
                        // result is a boolean true; otherwise the result is a boolean false
                        Constant constVal = expr1.generateConstant(code, type, errs);
                        return pool.valTrue().equals(constVal)
                                ? constVal
                                : expr2.generateConstant(code, type, errs);
                        }
                    break;

                case BIT_AND:
                    // TODO integer

                    // fall through for logical boolean "and"
                case COND_AND:
                    if (type.equals(pool.typeBoolean()))
                        {
                        // if the first expression is a boolean false, then the result is a boolean
                        // false;  otherwise if the second expression is a boolean true, then the
                        // result is a boolean true; otherwise the result is a boolean false
                        Constant constVal = expr1.generateConstant(code, type, errs);
                        return pool.valFalse().equals(constVal)
                                ? constVal
                                : expr2.generateConstant(code, type, errs);
                        }
                    break;

                case BIT_XOR:
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
                    // applies to:
                    // Int8/16/32/64/128, VarInt
                    // UInt8/16/32/64/128, VarUInt
                    // Dec32/64/128, VarDec
                    // Float16/32/64/128, VarFloat
                    // String
                    // Type

                case SUB:
                case MUL:

                case DIVMOD:
                    // TODO same as DIV? or a Tuple result? and if so, then shouldn't all support that?
                    // fall through
                case DIV:

                case MOD:
                }
            }

        return super.generateConstant(code, type, errs);
        }

//            switch (operator.getId())
//                {
//                case COLON:
//                case COND_ELSE:
//                case COND_OR:
//                case COND_AND:
//                case BIT_OR:
//                case BIT_XOR:
//                case BIT_AND:
//                case COMP_EQ:
//                case COMP_NEQ:
//                case COMP_LT:
//                case COMP_GT:
//                case COMP_LTEQ:
//                case COMP_GTEQ:
//                case COMP_ORD:
//                case AS:
//                case IS:
//                case INSTANCEOF:
//                case DOTDOT:
//                case SHL:
//                case SHR:
//                case USHR:
//                case ADD:
//                case SUB:
//                case MUL:
//                case DIV:
//                case MOD:
//                case DIVMOD:
//                }


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
