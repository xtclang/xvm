package org.xvm.compiler.ast;


import java.math.BigDecimal;

import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;

import org.xvm.asm.constants.CharConstant;
import org.xvm.asm.constants.Float16Constant;
import org.xvm.asm.constants.LiteralConstant;
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
     * @return the literal token
     */
    public Token getLiteral()
        {
        return literal;
        }

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
    protected boolean hasSingleValueImpl()
        {
        return true;
        }

    @Override
    public TypeConstant getImplicitType(Context ctx)
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

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, TuplePref pref, ErrorListener errs)
        {
        TypeConstant typeLiteral  = getImplicitType(ctx);
        Constant     constLiteral = getLiteralConstant();

        TypeFit fit = TypeFit.Fit;
        if (typeRequired != null)
            {
            // determine whether or not the constant can provide the requested type
            fit = calcFit(ctx, typeLiteral, typeRequired, pref);
            if (!fit.isFit() || fit.isConverting())
                {
                // need to find a conversion (or in the case of "no fit", just pretend we're trying
                // to find a conversion, since there is none, and then when we try to convert, we
                // will log the appropriate error)
                MethodConstant idMethod = typeLiteral.ensureTypeInfo().findConversion(typeRequired);
                if (idMethod != null)
                    {
                    // use the return value from the conversion function to figure out what type the
                    // literal should be converted to, and then do the conversion here in the
                    // compiler (eventually, once boot-strapped into Ecstasy, the compiler will be
                    // able to rely on the runtime itself to do conversions, and using containers,
                    // can even do so for user code)
                    typeRequired = idMethod.getSignature().getRawReturns()[0];
                    }

                constLiteral = convertConstant(constLiteral, typeRequired, errs);
                typeLiteral  = fit.isFit() ? constLiteral.getType() : typeRequired;
                }
            }

        finishValidation(typeRequired, typeLiteral, fit, constLiteral, errs);
        return this;
        }


    // ----- helpers -------------------------------------------------------------------------------

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

    /**
     * Convert a constant value to the specified type.
     *
     * @param val   the constant value
     * @param type  the desired type
     * @param errs  the error listener to log to
     *
     * @return the converted constant value
     */
    public Constant convertConstant(Constant val, TypeConstant type, ErrorListener errs)
        {
        // TODO @Auto to<Object()>() - wraps around each of these

        ConstantPool pool = pool();
        switch (val.getFormat() + "->" + type.getEcstasyClassName())
            {
            case "Char->String":
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
                int n = 0;
                PackedInteger piVal = ((LiteralConstant) val).getPackedInteger();
                if (isIntInRange(piVal, 0, 1))
                    {
                    n = piVal.getInt();
                    }
                else
                    {
                    log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                            val.getType().getValueString(), val.getValueString());
                    }
                return pool.ensureBitConstant(n);
                }

            case "IntLiteral->Nibble":
                {
                int n = 0;
                PackedInteger piVal = ((LiteralConstant) val).getPackedInteger();
                if (isIntInRange(piVal, 0x0, 0xF))
                    {
                    n = piVal.getInt();
                    }
                else
                    {
                    log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                            val.getType().getValueString(), val.getValueString());
                    }
                return pool.ensureNibbleConstant(n);
                }

            case "IntLiteral->Int8":
                {
                PackedInteger piVal = ((LiteralConstant) val).getPackedInteger();
                if (!isIntInRange(piVal, Byte.MIN_VALUE, Byte.MAX_VALUE))
                    {
                    log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                            val.getType().getValueString(), val.getValueString());
                    piVal = PackedInteger.ZERO;
                    }
                return pool.ensureInt8Constant(piVal.getInt());
                }

            case "IntLiteral->Int16":
                {
                PackedInteger piVal = ((LiteralConstant) val).getPackedInteger();
                if (!isIntInRange(piVal, Short.MIN_VALUE, Short.MAX_VALUE))
                    {
                    log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                            val.getType().getValueString(), val.getValueString());
                    piVal = PackedInteger.ZERO;
                    }
                return pool.ensureIntConstant(piVal, Format.Int16);
                }

            case "IntLiteral->Int32":
                {
                PackedInteger piVal = ((LiteralConstant) val).getPackedInteger();
                if (!isIntInRange(piVal, Integer.MIN_VALUE, Integer.MAX_VALUE))
                    {
                    log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                            val.getType().getValueString(), val.getValueString());
                    piVal = PackedInteger.ZERO;
                    }
                return pool.ensureIntConstant(piVal, Format.Int32);
                }

            case "IntLiteral->Int64":
                {
                PackedInteger piVal = ((LiteralConstant) val).getPackedInteger();
                if (!isIntInRange(piVal, Long.MIN_VALUE, Long.MAX_VALUE))
                    {
                    log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                            val.getType().getValueString(), val.getValueString());
                    piVal = PackedInteger.ZERO;
                    }
                return pool.ensureIntConstant(piVal);
                }

            case "IntLiteral->Int128":
                {
                PackedInteger piVal = ((LiteralConstant) val).getPackedInteger();
                if (piVal.isBig() && piVal.getSignedByteSize() > 16)
                    {
                    log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                            val.getType().getValueString(), val.getValueString());
                    piVal = PackedInteger.ZERO;
                    }
                return pool.ensureIntConstant(piVal, Format.Int128);
                }

            case "IntLiteral->VarInt":
                {
                PackedInteger piVal = ((LiteralConstant) val).getPackedInteger();
                return pool.ensureIntConstant(piVal, Format.VarInt);
                }

            case "IntLiteral->UInt8":
                {
                PackedInteger piVal = ((LiteralConstant) val).getPackedInteger();
                if (!isIntInRange(piVal, 0x00, 0xFF))
                    {
                    log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                            val.getType().getValueString(), val.getValueString());
                    piVal = PackedInteger.ZERO;
                    }
                return pool.ensureUInt8Constant(piVal.getInt());
                }

            case "IntLiteral->UInt16":
                {
                PackedInteger piVal = ((LiteralConstant) val).getPackedInteger();
                if (!isIntInRange(piVal, 0x0000, 0xFFFF))
                    {
                    log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                            val.getType().getValueString(), val.getValueString());
                    piVal = PackedInteger.ZERO;
                    }
                return pool.ensureIntConstant(piVal, Format.UInt16);
                }

            case "IntLiteral->UInt32":
                {
                PackedInteger piVal = ((LiteralConstant) val).getPackedInteger();
                if (!isIntInRange(piVal, 0x00000000L, 0xFFFFFFFFL))
                    {
                    log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                            val.getType().getValueString(), val.getValueString());
                    piVal = PackedInteger.ZERO;
                    }
                return pool.ensureIntConstant(piVal, Format.UInt32);
                }

            case "IntLiteral->UInt64":
                {
                PackedInteger piVal = ((LiteralConstant) val).getPackedInteger();
                if (piVal.isNegative() || piVal.isBig() && piVal.getUnsignedByteSize() > 8)
                    {
                    log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                            val.getType().getValueString(), val.getValueString());
                    piVal = PackedInteger.ZERO;
                    }
                return pool.ensureIntConstant(piVal, Format.UInt64);
                }

            case "IntLiteral->UInt128":
                {
                PackedInteger piVal = ((LiteralConstant) val).getPackedInteger();
                if (piVal.isNegative() || piVal.isBig() && piVal.getUnsignedByteSize() > 16)
                    {
                    log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                            val.getType().getValueString(), val.getValueString());
                    piVal = PackedInteger.ZERO;
                    }
                return pool.ensureIntConstant(piVal, Format.UInt128);
                }

            case "IntLiteral->VarUInt":
                {
                PackedInteger piVal = ((LiteralConstant) val).getPackedInteger();
                if (piVal.isNegative())
                    {
                    log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                            val.getType().getValueString(), val.getValueString());
                    piVal = PackedInteger.ZERO;
                    }
                return pool.ensureIntConstant(piVal, Format.VarUInt);
                }

            case "IntLiteral->Dec32":
            case "FPLiteral->Dec32":
                {
                try
                    {
                    BigDecimal bigdec = ((LiteralConstant) val).getBigDecimal();
                    return pool.ensureDecimalConstant(new Decimal32(bigdec));
                    }
                catch (ArithmeticException e)
                    {
                    log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                            val.getType().getValueString(), val.getValueString());
                    return pool.ensureDecimalConstant(new Decimal32(BigDecimal.ZERO));
                    }
                }

            case "IntLiteral->Dec64":
            case "FPLiteral->Dec64":
                {
                try
                    {
                    BigDecimal bigdec = ((LiteralConstant) val).getBigDecimal();
                    return pool.ensureDecimalConstant(new Decimal64(bigdec));
                    }
                catch (ArithmeticException e)
                    {
                    log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                            val.getType().getValueString(), val.getValueString());
                    return pool.ensureDecimalConstant(new Decimal64(BigDecimal.ZERO));
                    }
                }

            case "IntLiteral->Dec128":
            case "FPLiteral->Dec128":
                {
                try
                    {
                    BigDecimal bigdec = ((LiteralConstant) val).getBigDecimal();
                    return pool.ensureDecimalConstant(new Decimal128(bigdec));
                    }
                catch (ArithmeticException e)
                    {
                    log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                            val.getType().getValueString(), val.getValueString());
                    return pool.ensureDecimalConstant(new Decimal128(BigDecimal.ZERO));
                    }
                }

            case "IntLiteral->VarDec":
            case "FPLiteral->VarDec":
                {
                BigDecimal bigdec = ((LiteralConstant) val).getBigDecimal();
                // TODO - support variable-length decimal
                throw new UnsupportedOperationException("var-len decimal not implemented");
                }

            case "IntLiteral->Float16":
            case "FPLiteral->Float16":
                {
                // TODO add support for *purposeful* NaN/infinity
                float   flVal = 0;
                boolean fErr  = false;
                try
                    {
                    flVal = ((LiteralConstant) val).getFloat();
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
                            val.getType().getValueString(), val.getValueString());
                    flVal = 0;
                    }
                return pool.ensureFloat16Constant(flVal);
                }

            case "IntLiteral->Float32":
            case "FPLiteral->Float32":
                {
                // TODO add support for *purposeful* NaN/infinity
                float   flVal = 0;
                boolean fErr  = false;
                try
                    {
                    flVal = ((LiteralConstant) val).getFloat();
                    }
                catch (NumberFormatException e)
                    {
                    fErr = true;
                    }
                if (fErr || !Float.isFinite(flVal))
                    {
                    log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                            val.getType().getValueString(), val.getValueString());
                    flVal = 0;
                    }
                return pool.ensureFloat32Constant(flVal);
                }

            case "IntLiteral->Float64":
            case "FPLiteral->Float64":
                {
                // TODO add support for *purposeful* NaN/infinity
                double  flVal = 0;
                boolean fErr  = false;
                try
                    {
                    flVal = ((LiteralConstant) val).getDouble();
                    }
                catch (NumberFormatException e)
                    {
                    fErr = true;
                    }
                if (fErr || !Double.isFinite(flVal))
                    {
                    log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                            val.getType().getValueString(), val.getValueString());
                    flVal = 0;
                    }
                return pool.ensureFloat64Constant(flVal);
                }

            case "IntLiteral->Float128":
            case "FPLiteral->Float128":
                // TODO - support 128-bit float
                throw new UnsupportedOperationException("128-bit binary floating point not implemented");

            case "IntLiteral->VarFloat":
            case "FPLiteral->VarFloat":
                // TODO - support var-len float
                throw new UnsupportedOperationException("var-len binary floating point not implemented");

            default:
                log(errs, Severity.ERROR, Compiler.WRONG_TYPE,
                        type.getValueString(), val.getType().getValueString());
                return Constant.defaultValue(type);
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
