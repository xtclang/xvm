package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.GP_Sub;
import org.xvm.asm.op.Invoke_01;
import org.xvm.asm.op.P_Get;

import org.xvm.util.PackedInteger;


/**
 * An expression that converts the result of another expression to an Ecstasy Int64, using the rules
 * defined by {@link TypeConstant#isIntConvertible()} and {@link Constant#getIntValue()}.
 * <p/>
 * Steps, depending on the type of the underlying expression:
 * <ul>
 * <li>Extract: For Bit, Nibble, Char, and Enum types, the Int value is extracted from the
 *     underlying expression, resulting in an Int type, so at this point the resulting type is
 *     IntNumber, regardless of the type of the underlying expression;</li>
 * <li>Offset: The optional offset is applied to the IntNumber;</li>
 * <li>Convert: If the IntNumber is not an Int, then the IntNumber.to&lt;Int&gt;() method is
 *     applied.</li>
 * </ul>
 */
public class ToIntExpression
        extends SyntheticExpression
    {
    // ----- constructors --------------------------------------------------------------------------

    /**
     * Construct a ToIntExpression.
     *
     * @param expr        the expression to convert to an int
     * @param pintOffset  the (optional) value to add to the expression being converted
     * @param errs        the error list to log to
     */
    public ToIntExpression(Expression expr, PackedInteger pintOffset, ErrorListener errs)
        {
        super(expr);

        assert expr.getType().isIntConvertible();

        Constant val = null;
        if (expr.isConstant())
            {
            // determine if compile-time conversion is supported
            Constant      constOrig = expr.toConstant();
            PackedInteger pintVal   = constOrig.getIntValue();
            if (pintOffset != null)
                {
                pintVal = pintVal.sub(pintOffset);
                }
            val = pool().ensureIntConstant(pintVal);
            }

        m_pintOffset = pintOffset;
        finishValidation(null, expr.pool().typeInt(), expr.getTypeFit().addConversion(), val, errs);
        }


    // ----- accessors -----------------------------------------------------------------------------

    /**
     * @return the method or property constant to use to extract an IntNumber, or null if extraction
     *         is unnecessary
     */
    public IdentityConstant getExtractor()
        {
        switch (expr.getType().getEcstasyClassName())
            {
            case "Int8":
            case "Int16":
            case "Int32":
            case "Int64":
            case "Int128":
            case "VarInt":
            case "UInt8":
            case "UInt16":
            case "UInt32":
            case "UInt64":
            case "UInt128":
            case "VarUInt":
                // already an IntNumber
                return null;

            case "Bit":
            case "Nibble":
            case "Char":
                {
                // at least one of these does NOT have an @Auto method that converts to<Int>()
                MethodConstant id = expr.getType().ensureTypeInfo().findCallable(
                        "to", true, false, getTypes(), TypeConstant.NO_TYPES, null);
                assert id != null;
                return id;
                }

            default:
                {
                PropertyConstant id = expr.getType().ensureTypeInfo().findProperty("ordinal").getIdentity();
                assert id != null;
                return id;
                }
            }
        }

    /**
     * @return the magnitude of the offset that will be subtracted from the int value of the
     *         underlying expression, or null
     */
    public PackedInteger getOffset()
        {
        return m_pintOffset;
        }

    /**
     * @return the constant to use as an offset, or null if no offset adjustment is necessary
     */
    public Constant getOffsetConstant()
        {
        PackedInteger pint = getOffset();
        if (pint == null || pint.equals(PackedInteger.ZERO))
            {
            return null;
            }

        ConstantPool pool    = pool();
        String       sFormat = expr.getType().getEcstasyClassName();
        switch (sFormat)
            {
            case "Int8":
                return pool.ensureInt8Constant(pint.getInt());

            case "UInt8":
                return pool.ensureUInt8Constant(pint.getInt());

            case "Int16":
            case "Int32":
            case "Int128":
            case "VarInt":
            case "UInt16":
            case "UInt32":
            case "UInt64":
            case "UInt128":
            case "VarUInt":
                return pool.ensureIntConstant(pint, Format.valueOf(sFormat));

            case "Bit":     // converted by extract
            case "Nibble":  // converted by extract
            case "Char":    // converted by extract
            case "Int64":
            default:
                return pool.ensureIntConstant(pint);
            }
        }

    /**
     * @return the method to use to extract an IntNumber, or null if extraction is unnecessary
     */
    public MethodConstant getConvertMethod()
        {
        switch (expr.getType().getEcstasyClassName())
            {
            case "Int8":
            case "Int16":
            case "Int32":
            case "Int128":
            case "VarInt":
            case "UInt8":
            case "UInt16":
            case "UInt32":
            case "UInt64":
            case "UInt128":
            case "VarUInt":
                // most of these do NOT have an @Auto method that converts to<Int>()
                MethodConstant id = expr.getType().ensureTypeInfo().findCallable(
                        "to", true, false, getTypes(), TypeConstant.NO_TYPES, null);
                assert id != null;
                return id;


            case "Bit":     // converted by extract
            case "Nibble":  // converted by extract
            case "Char":    // converted by extract
            case "Int64":   // already the right type
            default:
                return null;
            }
        }


    // ----- Expression compilation ----------------------------------------------------------------

    @Override
    public TypeConstant getImplicitType(Context ctx)
        {
        return getType();
        }

    @Override
    protected Expression validate(Context ctx, TypeConstant typeRequired, ErrorListener errs)
        {
        return this;
        }

    @Override
    public void generateVoid(Context ctx, Code code, ErrorListener errs)
        {
        expr.generateVoid(ctx, code, errs);
        }

    @Override
    public Argument generateArgument(
            Context ctx, Code code, boolean fLocalPropOk, boolean fUsedOnce, ErrorListener errs)
        {
        return !isConstant() && getExtractor() == null && getOffsetConstant() == null && getConvertMethod() == null
                ? expr.generateArgument(ctx, code, fLocalPropOk, fUsedOnce, errs)
                : super.generateArgument(ctx, code, fLocalPropOk, fUsedOnce, errs);
        }

    @Override
    public void generateAssignment(Context ctx, Code code, Assignable LVal, ErrorListener errs)
        {
        if (isConstant())
            {
            super.generateAssignment(ctx, code, LVal, errs);
            }

        IdentityConstant idExtract   = getExtractor();
        Constant         constOffset = getOffsetConstant();
        MethodConstant   idConvert   = getConvertMethod();

        // step 1: extract the value from the underlying expression
        Argument argExtracted = expr.generateArgument(ctx, code, false, true, errs);
        if (idExtract != null)
            {
            // extract always results in an Int64, which is the type of this expression
            Argument   argExtractFrom = argExtracted;
            Assignable LValExtractTo  = createTempVar(code, getType(), true, errs);
            argExtracted = LValExtractTo.getLocalArgument();
            code.add(idExtract instanceof PropertyConstant
                    ? new P_Get((PropertyConstant) idExtract, argExtractFrom, argExtracted)
                    : new Invoke_01(argExtractFrom, (MethodConstant) idExtract, argExtracted));
            }

        // step 2: apply the offset
        Argument argAdjusted = argExtracted;
        if (constOffset != null)
            {
            Argument   argAdjustFrom = argAdjusted;
            Assignable LValAdjustTo  = createTempVar(code, argExtracted.getType(), true, errs);
            argAdjusted = LValAdjustTo.getLocalArgument();
            code.add(new GP_Sub(argAdjustFrom, constOffset, argAdjusted));
            }

        // step 3: apply the conversion
        if (idConvert == null)
            {
            LVal.assign(argAdjusted, code, errs);
            }
        else
            {
            Assignable LValConverted = LVal.isLocalArgument()
                    ? LVal
                    : createTempVar(code, getType(), true, errs);

            code.add(new Invoke_01(argAdjusted, idConvert, LValConverted.getLocalArgument()));

            if (LVal != LValConverted)
                {
                LVal.assign(LValConverted.getLocalArgument(), code, errs);
                }
            }
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        StringBuilder sb = new StringBuilder();

        sb.append(expr);

        IdentityConstant idExtract = getExtractor();
        if (idExtract != null)
            {
            sb.append('.')
              .append(idExtract.getName());
            if (idExtract instanceof MethodConstant)
                {
                sb.append("()");
                }
            }

        PackedInteger pintOffset = getOffset();
        if (pintOffset != null)
            {
            if (pintOffset.equals(PackedInteger.ZERO))
                {
                pintOffset = null;
                }
            else
                {
                sb.append(" - ")
                  .append(pintOffset);
                }
            }

        MethodConstant idConvert = getConvertMethod();
        if (idConvert != null)
            {
            if (pintOffset != null)
                {
                sb.insert(0, '(')
                        .append(')');
                }

            sb.append('.')
              .append(idConvert.getName())
              .append("()");
            }

        return sb.toString();
        }


    // ----- fields --------------------------------------------------------------------------------

    private PackedInteger m_pintOffset;
    }
