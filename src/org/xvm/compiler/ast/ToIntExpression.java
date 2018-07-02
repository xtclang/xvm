package org.xvm.compiler.ast;


import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.ErrorListener;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op;
import org.xvm.asm.Register;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Invoke_01;
import org.xvm.asm.op.Var;

import org.xvm.compiler.ast.Statement.Context;

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
            val = constOrig.getConstantPool().ensureIntConstant(pintVal);
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
                // at least one of these does NOT have an @Auto method that converts to<Int>()
                {
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
    public void generateVoid(Code code, ErrorListener errs)
        {
        expr.generateVoid(code, errs);
        }

    @Override
    public void generateAssignment(Code code, Assignable LVal, ErrorListener errs)
        {
        if (isConstant() || !LVal.isNormalVariable())
            {
            super.generateAssignment(code, LVal, errs);
            }


        if (m_iVal != 0)
            {
            getUnderlyingExpression().generateAssignment(code, LVal, errs);
            return;
            }

        if (isConstant())
            {
            super.generateAssignment(code, LVal, errs);
            return;
            }

        // get the value to be converted
        Argument argIn = getUnderlyingExpression().generateArgument(code, true, true, errs);

        // determine the destination of the conversion
        if (LVal.isLocalArgument())
            {
            code.add(new Invoke_01(argIn, m_idConv, LVal.getLocalArgument()));
            }
        else
            {
            Register regResult = new Register(getType(), Op.A_STACK);
            code.add(new Invoke_01(argIn, m_idConv, regResult));
            LVal.assign(regResult, code, errs);
            }
        }


    // ----- debugging assistance ------------------------------------------------------------------

    @Override
    public String toString()
        {
        return getUnderlyingExpression().toString()
                + '.' + m_idConv.getName()
                + '<' + getType().getValueString() + ">()";
        }


    // ----- fields --------------------------------------------------------------------------------

    private PackedInteger m_pintOffset;
    }
