package org.xvm.compiler.ast;


import java.math.BigDecimal;

import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.Version;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

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
     * @return the version value
     */
    public Version getVersion()
        {
        assert literal.getId() == Id.LIT_VERSION;
        return (Version) literal.getValue();
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
    public TypeConstant getImplicitType(Context ctx)
        {
        ConstantPool pool = pool();
        switch (literal.getId())
            {
            case LIT_BIT:
                return pool.typeBit();

            case LIT_NIBBLE:
                return pool.typeNibble();

            case LIT_CHAR:
                return pool.typeChar();

            case TODO:              // the T0D0 keyword has a String text for the token's value
            case LIT_STRING:
                return pool.typeString();

            case LIT_BINSTR:
                return pool.typeBinary();

            case LIT_INT:
                return pool.typeIntLiteral();
            case LIT_INTA:
                return pool.typeInt();
            case LIT_INT8:
                return pool.typeCInt8();
            case LIT_INT16:
                return pool.typeCInt16();
            case LIT_INT32:
                return pool.typeCInt32();
            case LIT_INT64:
                return pool.typeCInt64();
            case LIT_INT128:
                return pool.typeCInt128();
            case LIT_INTN:
                return pool.typeCIntN();

            case LIT_UINTA:
                return pool.typeUInt();
            case LIT_UINT8:
                return pool.typeCUInt8();
            case LIT_UINT16:
                return pool.typeCUInt16();
            case LIT_UINT32:
                return pool.typeCUInt32();
            case LIT_UINT64:
                return pool.typeCUInt64();
            case LIT_UINT128:
                return pool.typeCUInt128();
            case LIT_UINTN:
                return pool.typeCUIntN();

            case LIT_DEC:
                return pool.typeFPLiteral();
            case LIT_DECA:
                return pool.typeDec();
            case LIT_DEC32:
            case LIT_DEC64:
            case LIT_DEC128:
            case LIT_DECN:
                return pool.ensureEcstasyTypeConstant("numbers." + literal.getId().TEXT);

            case LIT_FLOAT:
                return pool.typeFPLiteral();
            case LIT_FLOAT8E4:
            case LIT_FLOAT8E5:
            case LIT_BFLOAT16:
            case LIT_FLOAT16:
            case LIT_FLOAT32:
            case LIT_FLOAT64:
            case LIT_FLOAT128:
            case LIT_FLOATN:
                return pool.ensureEcstasyTypeConstant("numbers." + literal.getId().TEXT);

            case LIT_DATE:
                return pool.typeDate();

            case LIT_TIMEOFDAY:
                return pool.typeTimeOfDay();

            case LIT_TIME:
                return pool.typeTime();

            case LIT_TIMEZONE:
                return pool.typeTimeZone();

            case LIT_DURATION:
                return pool.typeDuration();

            case LIT_VERSION:
                return pool.typeVersion();

            case LIT_PATH:
                return pool.typePath();

            default:
                throw new IllegalStateException(literal.getId().name() + "=" + literal.getValue());
            }
        }

    /**
     * Take a unary prefix token ('+' or '-') and prepend it to this literal.
     *
     * @param tokenPrefix  the unary prefix token
     * @param errs         an error list to log any errors to
     *
     * @return the expression to use in place of this expression
     */
    Expression adoptUnaryPrefix(Token tokenPrefix, ErrorListener errs)
        {
        assert tokenPrefix.getId() == Id.ADD || tokenPrefix.getId() == Id.SUB;

        // this must be a numeric literal
        Object  oValue;
        boolean fNegate   = tokenPrefix.getId() == Id.SUB;
        Token   tokNumber = literal;
        switch (tokNumber.getId())
            {
            default:
                tokenPrefix.log(errs, getSource(), Severity.ERROR, Compiler.MISSING_OPERATOR,
                        tokenPrefix.getValueText(), getImplicitType(null).getValueString());
                return this;

            case LIT_INT:
            case LIT_INTA:
            case LIT_INT8:
            case LIT_INT16:
            case LIT_INT32:
            case LIT_INT64:
            case LIT_INT128:
            case LIT_INTN:
            case LIT_UINTA:
            case LIT_UINT8:
            case LIT_UINT16:
            case LIT_UINT32:
            case LIT_UINT64:
            case LIT_UINT128:
            case LIT_UINTN:
                PackedInteger pint = (PackedInteger) tokNumber.getValue();
                oValue = fNegate ? PackedInteger.ZERO.sub(pint) : pint;
                break;

            case LIT_DEC:
            case LIT_DECA:
            case LIT_DEC32:
            case LIT_DEC64:
            case LIT_DEC128:
            case LIT_DECN:
                BigDecimal dec = (BigDecimal) tokNumber.getValue();
                oValue = fNegate ? dec.negate() : dec;
                break;

            case LIT_FLOAT:
            case LIT_FLOAT8E4:
            case LIT_FLOAT8E5:
            case LIT_BFLOAT16:
            case LIT_FLOAT16:
            case LIT_FLOAT32:
            case LIT_FLOAT64:
            case LIT_FLOAT128:
            case LIT_FLOATN:
                // it's just a string
                oValue = getSource().toString(tokenPrefix.getStartPosition(), tokNumber.getEndPosition());
                break;
            }

        Token tokenMerged = new Token(
                tokenPrefix.getStartPosition(),
                tokNumber.getEndPosition(),
                tokNumber.getId(),
                oValue);
        return replaceThisWith(new LiteralExpression(tokenMerged));
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        TypeConstant typeActual = getImplicitType(ctx);
        Constant     constVal;

        try
            {
            constVal = getLiteralConstant();
            }
        catch (RuntimeException e)
            {
            log(errs, Severity.ERROR, Compiler.BAD_LITERAL, toString());
            return null;
            }

        assert constVal != null;
        return finishValidation(ctx, typeRequired, typeActual, TypeFit.Fit, constVal, errs);
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
        Format       format;
        switch (literal.getId())
            {
            case LIT_BIT:
                format = Format.Bit;
                break;

            case LIT_NIBBLE:
                format = Format.Nibble;
                break;

            case LIT_CHAR:
                return pool.ensureCharConstant(((Character) literal.getValue()).charValue());

            case TODO:              // the T0D0 keyword has a String text for the token's value
            case LIT_STRING:
                return pool.ensureStringConstant((String) literal.getValue());

            case LIT_BINSTR:
                return pool.ensureByteStringConstant((byte[]) literal.getValue());

            case LIT_INT:
                format = Format.IntLiteral;
                break;

            case LIT_INTA:
                format = Format.Int;
                break;

            case LIT_INT8:
                format = Format.CInt8;
                break;

            case LIT_INT16:
                format = Format.CInt16;
                break;

            case LIT_INT32:
                format = Format.CInt32;
                break;

            case LIT_INT64:
                format = Format.CInt64;
                break;

            case LIT_INT128:
                format = Format.CInt128;
                break;

            case LIT_INTN:
                format = Format.CIntN;
                break;

            case LIT_UINTA:
                format = Format.UInt;
                break;

            case LIT_UINT8:
                format = Format.CUInt8;
                break;

            case LIT_UINT16:
                format = Format.CUInt16;
                break;

            case LIT_UINT32:
                format = Format.CUInt32;
                break;

            case LIT_UINT64:
                format = Format.CUInt64;
                break;

            case LIT_UINT128:
                format = Format.CUInt128;
                break;

            case LIT_UINTN:
                format = Format.CUIntN;
                break;

            case LIT_DEC:
            case LIT_FLOAT:
                format = Format.FPLiteral;
                break;

            case LIT_DECA:
                format = Format.Dec;
                break;

            case LIT_DEC32:
                format = Format.Dec32;
                break;

            case LIT_DEC64:
                format = Format.Dec64;
                break;

            case LIT_DEC128:
                format = Format.Dec128;
                break;

            case LIT_DECN:
                format = Format.DecN;
                break;

            case LIT_FLOAT8E4:
                format = Format.Float8e4;
                break;

            case LIT_FLOAT8E5:
                format = Format.Float8e5;
                break;

            case LIT_BFLOAT16:
                format = Format.BFloat16;
                break;

            case LIT_FLOAT16:
                format = Format.Float16;
                break;

            case LIT_FLOAT32:
                format = Format.Float32;
                break;

            case LIT_FLOAT64:
                format = Format.Float64;
                break;

            case LIT_FLOAT128:
                format = Format.Float128;
                break;

            case LIT_FLOATN:
                format = Format.FloatN;
                break;

            case LIT_DATE:
                return pool.ensureLiteralConstant(Format.Date, (String) literal.getValue());

            case LIT_TIMEOFDAY:
                return pool.ensureLiteralConstant(Format.TimeOfDay, (String) literal.getValue());

            case LIT_TIME:
                return pool.ensureLiteralConstant(Format.Time, (String) literal.getValue());

            case LIT_TIMEZONE:
                return pool.ensureLiteralConstant(Format.TimeZone, (String) literal.getValue());

            case LIT_DURATION:
                return pool.ensureLiteralConstant(Format.Duration, (String) literal.getValue());

            case LIT_VERSION:
                return pool.ensureVersionConstant((Version) literal.getValue());

            case LIT_PATH:
                return pool.ensureLiteralConstant(Format.Path, (String) literal.getValue());

            default:
                throw new IllegalStateException(literal.getId().name() + "=" + literal.getValue());
            }

        return pool.ensureLiteralConstant(format, literal.getString(getSource()), literal.getValue());
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        switch (literal.getId())
            {
            case LIT_INT:
            case LIT_DEC:
            case LIT_FLOAT:
                return String.valueOf(literal.getValue());

            case LIT_BIT:
            case LIT_NIBBLE:
            case LIT_INTA:
            case LIT_INT8:
            case LIT_INT16:
            case LIT_INT32:
            case LIT_INT64:
            case LIT_INT128:
            case LIT_INTN:
            case LIT_UINTA:
            case LIT_UINT8:
            case LIT_UINT16:
            case LIT_UINT32:
            case LIT_UINT64:
            case LIT_UINT128:
            case LIT_UINTN:
            case LIT_DECA:
            case LIT_DEC32:
            case LIT_DEC64:
            case LIT_DEC128:
            case LIT_DECN:
            case LIT_FLOAT8E4:
            case LIT_FLOAT8E5:
            case LIT_BFLOAT16:
            case LIT_FLOAT16:
            case LIT_FLOAT32:
            case LIT_FLOAT64:
            case LIT_FLOAT128:
            case LIT_FLOATN:
                return literal.toString();

            case LIT_CHAR:
                 return Handy.quotedChar((Character) literal.getValue());

            case LIT_STRING:
                 return Handy.quotedString(String.valueOf(literal.getValue()));

            case LIT_BINSTR:
                return '#' + Handy.byteArrayToHexString((byte[]) literal.getValue()).substring(2);

            case LIT_DATE:
                return "Date:" + literal.getValue();

            case LIT_TIMEOFDAY:
                return "TimeOfDay:" + literal.getValue();

            case LIT_TIME:
                return "Time:" + literal.getValue();

            case LIT_TIMEZONE:
                return "TimeZone:" + literal.getValue();

            case LIT_DURATION:
                return "Duration:" + literal.getValue();

            case LIT_VERSION:
                return "v:" + literal.getValue();

            case LIT_PATH:
                return "Path:" + literal.getValue();

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