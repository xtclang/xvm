package org.xvm.compiler.ast;


import java.math.BigDecimal;

import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.CharConstant;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.Float16Constant;
import org.xvm.asm.constants.ImmutableTypeConstant;
import org.xvm.asm.constants.IntConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.compiler.ast.Statement.Context;

import org.xvm.type.Decimal32;
import org.xvm.type.Decimal64;
import org.xvm.type.Decimal128;

import org.xvm.util.Handy;
import org.xvm.util.PackedInteger;
import org.xvm.util.Severity;


/**
 * A literal expression specifies a literal value.
 */
public class LiteralExpression
        extends Expression
    {
    // ----- constructors --------------------------------------------------------------------------

    public LiteralExpression(Token literal)
        {
        this.literal = literal;
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return true iff the LiteralExpression is the result of an empty T0D0 expression
     */
    public boolean isTODO()
        {
        return literal.getId() == Id.TODO;
        }

    @Override
    public long getStartPosition()
        {
        return literal.getStartPosition();
        }

    @Override
    public long getEndPosition()
        {
        return literal.getEndPosition();
        }


    // ----- compilation ---------------------------------------------------------------------------

    @Override
    public TypeFit testFit(Context ctx, TypeConstant typeRequired, TuplePref pref)
        {
        return calcFit(ctx, getLiteralType(), typeRequired, pref);
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, TuplePref pref, ErrorListener errs)
        {
        TypeConstant typeLiteral  = getLiteralType();
        Constant     constLiteral = getLiteralConstant();
        if (typeRequired != null && !typeLiteral.isA(typeRequired))
            {
            // need to find a conversion
            MethodConstant idMethod = typeLiteral.ensureTypeInfo().findConversion(typeRequired);
            if (idMethod == null)
                {
                // TODO log error
                finishValidation(TypeFit.Fit, typeRequired, generateFakeConstant(typeRequired));
                return null;
                }
            else
                {
                // use the return value from the conversion function to figure out what type the
                // literal should be converted to, and then do the conversion here in the compiler
                // (eventually, once boot-strapped into Ecstasy, the compiler will be able to rely
                // on the runtime itself to do conversions, and using containers, can even do so for
                // user code)
                TypeConstant typeConv = idMethod.getSignature().getRawReturns()[0];

                }
            }


//        TypeFit      fit         = calcFit(ctx, typeLiteral, typeRequired, pref);
//        if (fit == TypeFit.NoFit)
//            {
//            }
//        finishValidation(fit, );
        }


    // ----- helpers -------------------------------------------------------------------------------

    /**
     * @return the type that is implied by the literal token
     */
    public TypeConstant getLiteralType()
        {
        ConstantPool pool = pool();
        switch (literal.getId())
            {
            case LIT_CHAR:
                return pool.typeChar();

            case TODO:              // the T0D0 keyword has a String text for the token's value
            case LIT_STRING:
                return pool.typeString();

            case LIT_INT:
                return pool.typeIntLiteral();

            case LIT_DEC:
            case LIT_BIN:
                return pool.typeFPLiteral();

            default:
                throw new IllegalStateException(literal.getId().name() + "=" + literal.getValue());
            }
        }

    /**
     * @return the constant value of the literal token
     */
    public Constant getLiteralConstant()
        {
        // the LiteralExpression produces literal constants:
        // - Char
        // - String
        // - IntLiteral
        // - FPLiteral
        // other types are possible to obtain because there are conversion methods on the literal
        // types that provide support for other constant types
        ConstantPool pool = pool();
        switch (literal.getId())
            {
            case LIT_CHAR:
                return pool.ensureCharConstant(((Character) literal.getValue()).charValue());

            case TODO:              // the T0D0 keyword has a String text for the token's value
            case LIT_STRING:
                return pool.ensureStringConstant((String) literal.getValue());

            case LIT_INT:
                return pool.ensureLiteralConstant(Format.IntLiteral, literal.getString(getSource()));

            case LIT_DEC:
            case LIT_BIN:
                return pool.ensureLiteralConstant(Format.FPLiteral, literal.getString(getSource()));

            default:
                throw new IllegalStateException(literal.getId().name() + "=" + literal.getValue());
            }
        }

    public static Constant convertConstant(Constant val, TypeConstant type, ErrorListener errs)
        {
        ConstantPool pool = pool();
        switch (val.getType().getEcstasyClassName())
            {
            case "Char":
            case "String":
            case "IntLiteral":
            case "FPLiteral":
            default:
                throw new IllegalStateException("unsupported type: " + val.getType().getValueString());
            }

        switch (type.getEcstasyClassName())
            {
            case "String":
                {
                int ch = ((CharConstant) val).getValue();
                if (ch < Character.MIN_VALUE || ch > Character.MAX_VALUE)
                    {
                    log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                            val.getType().getValueString(), ch);
                    ch = '?';
                    }

                return pool.ensureStringConstant(Character.valueOf((char) ch).toString());
                }

            case "IntLiteral->Bit":
                {
                int n;
                PackedInteger piVal = ((IntConstant) val).getValue();
                if (isIntInRange(piVal, 0, 1))
                    {
                    n = piVal.getInt();
                    }
                else
                    {
                    n = 0;
                    log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                            val.getType().getValueString(), val.getValueString());
                    }
                return pool.ensureBitConstant(n);
                }

            case "IntLiteral->Nibble":
                if (literal.getId() == Id.LIT_INT)
                    {
                    int n;
                    if (isIntInRange(0x0, 0xF))
                        {
                        n = ((PackedInteger) literal.getValue()).getInt();
                        }
                    else
                        {
                        n = 0;
                        log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                                sName, literal.getString(getSource()));
                        }
                    return pool.ensureNibbleConstant(n);
                    }
                break;

            case "Int8":
                if (literal.getId() == Id.LIT_INT)
                    {
                    PackedInteger piVal = (PackedInteger) literal.getValue();
                    if (!isIntInRange(Byte.MIN_VALUE, Byte.MAX_VALUE))
                        {
                        log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                                sName, literal.getString(getSource()));
                        piVal = PackedInteger.ZERO;
                        }
                    return pool.ensureInt8Constant(piVal.getInt());
                    }
                break;

            case "Int16":
                if (literal.getId() == Id.LIT_INT)
                    {
                    PackedInteger piVal = (PackedInteger) literal.getValue();
                    if (!isIntInRange(Short.MIN_VALUE, Short.MAX_VALUE))
                        {
                        log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                                sName, literal.getString(getSource()));
                        piVal = PackedInteger.ZERO;
                        }
                    return pool.ensureIntConstant(piVal, Format.Int16);
                    }
                break;

            case "Int32":
                if (literal.getId() == Id.LIT_INT)
                    {
                    PackedInteger piVal = (PackedInteger) literal.getValue();
                    if (!isIntInRange(Integer.MIN_VALUE, Integer.MAX_VALUE))
                        {
                        log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                                sName, literal.getString(getSource()));
                        piVal = PackedInteger.ZERO;
                        }
                    return pool.ensureIntConstant(piVal, Format.Int32);
                    }
                break;

            case "Int64":
                if (literal.getId() == Id.LIT_INT)
                    {
                    PackedInteger piVal = (PackedInteger) literal.getValue();
                    if (!isIntInRange(Long.MIN_VALUE, Long.MAX_VALUE))
                        {
                        log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                                sName, literal.getString(getSource()));
                        piVal = PackedInteger.ZERO;
                        }
                    return pool.ensureIntConstant(piVal);
                    }
                break;

            case "Int128":
                if (literal.getId() == Id.LIT_INT)
                    {
                    PackedInteger piVal = (PackedInteger) literal.getValue();
                    if (piVal.isBig() && piVal.getSignedByteSize() > 16)
                        {
                        log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                                sName, literal.getString(getSource()));
                        piVal = PackedInteger.ZERO;
                        }
                    return pool.ensureIntConstant(piVal, Format.Int128);
                    }
                break;

            case "VarInt":
                if (literal.getId() == Id.LIT_INT)
                    {
                    return pool.ensureIntConstant(
                            (PackedInteger) literal.getValue(), Format.VarInt);
                    }
                break;

            case "UInt8":
                if (literal.getId() == Id.LIT_INT)
                    {
                    PackedInteger piVal = (PackedInteger) literal.getValue();
                    if (!isIntInRange(0x00, 0xFF))
                        {
                        log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                                sName, literal.getString(getSource()));
                        piVal = PackedInteger.ZERO;
                        }
                    return pool.ensureUInt8Constant(piVal.getInt());
                    }
                break;

            case "UInt16":
                if (literal.getId() == Id.LIT_INT)
                    {
                    PackedInteger piVal = (PackedInteger) literal.getValue();
                    if (!isIntInRange(0x0000, 0xFFFF))
                        {
                        log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                                sName, literal.getString(getSource()));
                        piVal = PackedInteger.ZERO;
                        }
                    return pool.ensureIntConstant(piVal, Format.UInt16);
                    }
                break;

            case "UInt32":
                if (literal.getId() == Id.LIT_INT)
                    {
                    PackedInteger piVal = (PackedInteger) literal.getValue();
                    if (!isIntInRange(0x00000000L, 0xFFFFFFFFL))
                        {
                        log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                                sName, literal.getString(getSource()));
                        piVal = PackedInteger.ZERO;
                        }
                    return pool.ensureIntConstant(piVal, Format.UInt32);
                    }
                break;

            case "UInt64":
                if (literal.getId() == Id.LIT_INT)
                    {
                    PackedInteger piVal = (PackedInteger) literal.getValue();
                    if (piVal.isNegative() || piVal.isBig() && piVal.getUnsignedByteSize() > 8)
                        {
                        log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                                sName, literal.getString(getSource()));
                        piVal = PackedInteger.ZERO;
                        }
                    return pool.ensureIntConstant(piVal, Format.UInt64);
                    }

            case "UInt128":
                if (literal.getId() == Id.LIT_INT)
                    {
                    PackedInteger piVal = (PackedInteger) literal.getValue();
                    if (piVal.isNegative() || piVal.isBig() && piVal.getUnsignedByteSize() > 16)
                        {
                        log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                                sName, literal.getString(getSource()));
                        piVal = PackedInteger.ZERO;
                        }
                    return pool.ensureIntConstant(piVal, Format.UInt128);
                    }

            case "VarUInt":
                if (literal.getId() == Id.LIT_INT)
                    {
                    PackedInteger piVal = (PackedInteger) literal.getValue();
                    if (piVal.isNegative())
                        {
                        log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                                sName, literal.getString(getSource()));
                        piVal = PackedInteger.ZERO;
                        }
                    return pool.ensureIntConstant(piVal, Format.VarUInt);
                    }

            case "Dec32":
                switch (literal.getId())
                    {
                    case LIT_INT:
                    case LIT_DEC:
                        BigDecimal bigdec = literal.getId() == Id.LIT_INT
                                ? new BigDecimal(((PackedInteger) literal.getValue()).getBigInteger())
                                : (BigDecimal) literal.getValue();
                        try
                            {
                            return pool.ensureDecimalConstant(new Decimal32(bigdec));
                            }
                        catch (ArithmeticException e) {}
                    }
                break;

            case "Dec64":
                switch (literal.getId())
                    {
                    case LIT_INT:
                    case LIT_DEC:
                        BigDecimal bigdec = literal.getId() == Id.LIT_INT
                                ? new BigDecimal(((PackedInteger) literal.getValue()).getBigInteger())
                                : (BigDecimal) literal.getValue();
                        try
                            {
                            return pool.ensureDecimalConstant(new Decimal64(bigdec));
                            }
                        catch (ArithmeticException e) {}
                    }
                break;

            case "Dec128":
                switch (literal.getId())
                    {
                    case LIT_INT:
                    case LIT_DEC:
                        BigDecimal bigdec = literal.getId() == Id.LIT_INT
                                ? new BigDecimal(((PackedInteger) literal.getValue()).getBigInteger())
                                : (BigDecimal) literal.getValue();
                        try
                            {
                            return pool.ensureDecimalConstant(new Decimal128(bigdec));
                            }
                        catch (ArithmeticException e) {}
                    }
                break;

            case "VarDec":
                switch (literal.getId())
                    {
                    case LIT_INT:
                    case LIT_DEC:
                        BigDecimal bigdec = literal.getId() == Id.LIT_INT
                                ? new BigDecimal(((PackedInteger) literal.getValue()).getBigInteger())
                                : (BigDecimal) literal.getValue();
                        // TODO - support variable-length decimal
                        throw new UnsupportedOperationException("var-len decimal not implemented");
                    }
                break;

            case "Float16":
                switch (literal.getId())
                    {
                    case LIT_INT:
                    case LIT_DEC:
                    case LIT_BIN: // TODO add support for *purposeful* NaN/infinity
                        float   flVal = 0;
                        boolean fErr  = false;
                        try
                            {
                            flVal = Float.parseFloat(literal.getValue().toString());
                            }
                        catch (NumberFormatException e)
                            {
                            fErr = true;
                            }
                        // convert to/from 16-bit to test for overflow/underflow
                        if (fErr || !Float.isFinite(flVal) || !Float.isFinite(
                                Float16Constant.toFloat(Float16Constant.toHalf(flVal))))
                            {
                            log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                                    sName, literal.getString(getSource()));
                            flVal = 0;
                            }
                        return pool.ensureFloat16Constant(flVal);
                    }
                break;

            case "Float32":
                switch (literal.getId())
                    {
                    case LIT_INT:
                    case LIT_DEC:
                    case LIT_BIN: // TODO add support for *purposeful* NaN/infinity
                        float   flVal = 0;
                        boolean fErr  = false;
                        try
                            {
                            flVal = Float.parseFloat(literal.getValue().toString());
                            }
                        catch (NumberFormatException e)
                            {
                            fErr = true;
                            }
                        if (fErr || !Float.isFinite(flVal))
                            {
                            log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                                    sName, literal.getString(getSource()));
                            flVal = 0;
                            }
                        return pool.ensureFloat32Constant(flVal);
                    }
                break;

            case "Float64":
                switch (literal.getId())
                    {
                    case LIT_INT:
                    case LIT_DEC:
                    case LIT_BIN: // TODO add support for *purposeful* NaN/infinity
                        double  flVal = 0;
                        boolean fErr  = false;
                        try
                            {
                            flVal = Double.parseDouble(literal.getValue().toString());
                            }
                        catch (NumberFormatException e)
                            {
                            fErr = true;
                            }
                        if (fErr || !Double.isFinite(flVal))
                            {
                            log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                                    sName, literal.getString(getSource()));
                            flVal = 0;
                            }
                        return pool.ensureFloat64Constant(flVal);
                    }
                break;

            case "Float128":
                // TODO - support 128-bit float
                throw new UnsupportedOperationException("128-bit binary floating point not implemented");

            case "VarFloat":
                // TODO - support var-len float
                throw new UnsupportedOperationException("var-len binary floating point not implemented");
            }
        }

    /**
     * Test the specified integer value to see if it in the specified (inclusive) range.
     *
     * @param piVal   the integer value
     * @param lLower  inclusive lower bound of the range
     * @param lUpper  inclusive upper bound of the range
     *
     * @return true iff the constant is in the specified range
     */
    private static boolean isIntInRange(PackedInteger piVal, long lLower, long lUpper)
        {
        return !piVal.isBig() && piVal.getLong() >= lLower && piVal.getLong() <= lUpper;
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        switch (literal.getId())
            {
            case LIT_INT:
            case LIT_DEC:
            case LIT_BIN:
                return String.valueOf(literal.getValue());

            case LIT_CHAR:
                 return Handy.quotedChar((Character) literal.getValue());

            case LIT_STRING:
                 return Handy.quotedString(String.valueOf(literal.getValue()));

            case TODO:
                return "TODO(" + Handy.quotedString(String.valueOf(literal.getValue())) + ')';

            default:
                throw new IllegalStateException(literal.getId().name() + "=" + literal.getValue());
            }
        }

    @Override
    public String getDumpDesc()
        {
        return toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    protected Token literal;
    }
