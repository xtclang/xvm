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
     * @return true iff the LiteralExpression is the result of an empty T0D0 expression
     */
    public boolean isTODO()
        {
        return literal.getId() == Id.TODO;
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
            case LIT_CHAR:
                return pool.typeChar();

            case TODO:              // the T0D0 keyword has a String text for the token's value
            case LIT_STRING:
                return pool.typeString();

            case LIT_BINSTR:
                return pool.typeBinary();

            case LIT_INT:
                return pool.typeIntLiteral();

            case LIT_DEC:
            case LIT_BIN:
                return pool.typeFPLiteral();

            case LIT_DATE:
                return pool.typeDate();

            case LIT_TIME:
                return pool.typeTime();

            case LIT_DATETIME:
                return pool.typeDateTime();

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
                PackedInteger pint = (PackedInteger) tokNumber.getValue();
                oValue = fNegate ? PackedInteger.ZERO.sub(pint) : pint;
                break;

            case LIT_DEC:
                BigDecimal dec = (BigDecimal) tokNumber.getValue();
                oValue = fNegate ? dec.negate() : dec;
                break;

            case LIT_BIN:
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
        Constant     constVal   = getLiteralConstant();

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
        switch (literal.getId())
            {
            case LIT_CHAR:
                return pool.ensureCharConstant(((Character) literal.getValue()).charValue());

            case TODO:              // the T0D0 keyword has a String text for the token's value
            case LIT_STRING:
                return pool.ensureStringConstant((String) literal.getValue());

            case LIT_BINSTR:
                return pool.ensureByteStringConstant((byte[]) literal.getValue());

            case LIT_INT:
                return pool.ensureLiteralConstant(Format.IntLiteral, literal.getString(getSource()), literal.getValue());

            case LIT_DEC:
            case LIT_BIN:
                return pool.ensureLiteralConstant(Format.FPLiteral, literal.getString(getSource()), literal.getValue());

            case LIT_DATE:
                return pool.ensureLiteralConstant(Format.Date, (String) literal.getValue());

            case LIT_TIME:
                return pool.ensureLiteralConstant(Format.Time, (String) literal.getValue());

            case LIT_DATETIME:
                return pool.ensureLiteralConstant(Format.DateTime, (String) literal.getValue());

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

            case LIT_BINSTR:
                return '#' + Handy.byteArrayToHexString((byte[]) literal.getValue()).substring(2);

            case LIT_DATE:
                return "Date:" + literal.getValue();

            case LIT_TIME:
                return "Time:" + literal.getValue();

            case LIT_DATETIME:
                return "DateTime:" + literal.getValue();

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
