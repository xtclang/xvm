package org.xvm.compiler.ast;


import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;
import org.xvm.asm.Register;

import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.op.Invoke_01;
import org.xvm.asm.op.Var;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.ErrorListener;
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
     * @return true iff the LiteralExpression is the result of an empty T0D0 expression
     */
    public boolean isTODO()
        {
        return literal.getId() == Id.TODO;
        }

    @Override
    public boolean isConstant()
        {
        return true;
        }

    @Override
    public Constant toConstant()
        {
        ConstantPool pool = getConstantPool();
        switch (literal.getId())
            {
            case LIT_CHAR:
                return pool.ensureCharConstant(((Character) literal.getValue()).charValue());

            case TODO:              // the T0D0 keyword has a String text for the token's value
            case LIT_STRING:
                return pool.ensureStringConstant((String) literal.getValue());

            case LIT_INT:
                PackedInteger piVal = (PackedInteger) literal.getValue();
                if (isIntInRange(Long.MIN_VALUE, Long.MAX_VALUE))
                    {
                    // any int up to 64-bit signed int range is assumed to be a 64-bit signed int
                    return pool.ensureIntConstant(piVal);
                    }
                else if (piVal.getSignedByteSize() <= 16)
                    {
                    // if it doesn't fit in 64-bits, any int value up to 128-bit signed int range is
                    // assumed to be a 128-bit signed int
                    return pool.ensureIntConstant(piVal, Format.Int128);
                    }
                else
                    {
                    // if it doesn't fit in 128-bit signed int, then use var-int
                    return pool.ensureIntConstant(piVal, Format.VarInt);
                    }

            case LIT_DEC:
                // TODO

            case LIT_BIN:
                // TODO

            default:
                throw new IllegalStateException(literal.getId().name() + "=" + literal.getValue());
            }
        }

    private boolean isIntInRange(long lLower, long lUpper)
        {
        if (literal.getId() != Id.LIT_INT)
            {
            return false;
            }

        PackedInteger piVal = (PackedInteger) literal.getValue();
        return !piVal.isBig() && piVal.getLong() >= lLower && piVal.getLong() <= lUpper;
        }

    @Override
    public Argument generateArgument(Code code, TypeConstant constType, boolean fTupleOk, ErrorListener errs)
        {
        if (constType.isSingleDefiningConstant()
                && constType.getDefiningConstant() instanceof ClassConstant
                && ((ClassConstant) constType.getDefiningConstant()).getModuleConstant().isEcstasyModule()
                && (!constType.isAccessSpecified() || constType.getAccess() == Access.PUBLIC))
            {
            ConstantPool  pool     = getConstantPool();
            ClassConstant constClz = (ClassConstant) constType.getDefiningConstant();
            String        sName    = constClz.getPathString();

            switch (sName)
                {
                // these are all of the super-classes and interfaces that could be represented by
                // a literal expression
                case "Object":
                case "Const":
                case "Orderable":
                case "collections.Hashable":
                    return toConstant();

                case "Sequential":
                    // char and the various ints are sequential
                    if (literal.getId() == Id.LIT_CHAR && literal.getId() == Id.LIT_INT)
                        {
                        return toConstant();
                        }
                    break;

                case "Number":
                    switch (literal.getId())
                        {
                        case LIT_INT:
                        case LIT_DEC:
                        case LIT_BIN:
                            return toConstant();
                        }
                    break;

                case "IntNumber":
                    if (literal.getId() == Id.LIT_INT)
                        {
                        return toConstant();
                        }
                    break;

                case "UIntNumber":
                    if (literal.getId() == Id.LIT_INT)
                        {
                        // unsigned value must be >= 0
                        PackedInteger piVal = (PackedInteger) literal.getValue();
                        if (piVal.isNegative())
                            {
                            log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                                    sName, literal.getString(getSource()));
                            piVal = PackedInteger.ZERO;
                            }

                        int cBytes = piVal.getUnsignedByteSize();
                        if (cBytes <= 8)
                            {
                            // as with "int", assume any unsigned int that fits in 64 bits is a
                            // 64-bit unsigned int
                            return pool.ensureIntConstant(piVal, Format.UInt64);
                            }
                        else if (cBytes <= 16)
                            {
                            // as with "int", if it doesn't fit in 64-bits, any int value up to
                            // 128-bit unsigned int range is assumed to be a 128-bit unsigned int
                            return pool.ensureIntConstant(piVal, Format.UInt128);
                            }
                        else
                            {
                            // if it doesn't fit in 128-bit unsigned int, then use var-uint
                            return pool.ensureIntConstant(piVal, Format.VarUInt);
                            }
                        }
                    break;

                case "FPNumber":
                    // TODO
                    break;

                case "DecimalFPNumber":
                    // TODO
                    break;

                case "BinaryFPNumber":
                    // TODO
                    break;

                case "Function":
                    {
                    // determine the constant value returned from the function (which is the value
                    // of this expression)
                    Argument argVal;
                    if (constType.isParamsSpecified())
                        {
                        // it has type params, so it must be a Function; see:
                        //      "@Auto function Object() to<function Object()>()"
                        List<TypeConstant> listParamTypes = constType.getParamTypes();
                        if (listParamTypes.size() == 2)
                            {
                            TypeConstant typeTParams = listParamTypes.get(0);
                            TypeConstant typeTReturn = listParamTypes.get(1);
                            if (typeTParams.isTuple()
                                    && typeTParams.getTupleFieldCount() == 0
                                    && typeTReturn.isTuple()
                                    && typeTReturn.getTupleFieldCount() == 1)
                                {
                                // call back into this expression and ask it to render itself as the
                                // return type from that function (a constant value), and then we'll
                                // wrap that with the conversion function (from Object)
                                argVal = generateArgument(
                                        code, typeTReturn.getTupleFieldType(0), false, errs);
                                }
                            else
                                {
                                // error: function must take no parameters and return one value;
                                // drop into the generic handling of the request for error handling
                                break;
                                }
                            }
                        else
                            {
                            // error: function must have 2 parameters (t-params & t-returns);
                            // drop into the generic handling of the request for error handling
                            break;
                            }
                        }
                    else
                        {
                        argVal = toConstant();
                        }

                    // create a constant for this method on Object:
                    //      "@Auto function Object() to<function Object()>()"
                    TypeConstant   typeTuple   = pool.ensureEcstasyTypeConstant("collections.Tuple");
                    TypeConstant   typeTParams = pool.ensureParameterizedTypeConstant(
                            typeTuple, SignatureConstant.NO_TYPES);
                    TypeConstant   typeTReturn = pool.ensureParameterizedTypeConstant(
                            typeTuple, new TypeConstant[] {pool.ensureThisTypeConstant(null)});
                    TypeConstant   typeFn      = pool.ensureParameterizedTypeConstant(
                            pool.ensureEcstasyTypeConstant("Function"),
                            new TypeConstant[] {typeTParams, typeTReturn});
                    MethodConstant methodTo    = pool.ensureMethodConstant(
                            pool.ensureEcstasyClassConstant("Object"), "to", Access.PUBLIC,
                            new TypeConstant[] {typeFn}, SignatureConstant.NO_TYPES);

                    // generate the code that turns the constant value from this expression into a
                    // function object that returns that value
                    Register argResult = new Register(typeFn);
                    code.add(new Var(argResult));
                    code.add(new Invoke_01(argVal, methodTo, argResult));
                    return argResult;
                    }

                // untyped literals
                case "IntLiteral":
                    if (literal.getId() == Id.LIT_INT)
                        {
                        return pool.ensureLiteralConstant(
                                Format.IntLiteral, literal.getString(getSource()));
                        }
                    break;

                case "FPLiteral":
                    switch (literal.getId())
                        {
                        case LIT_INT:
                        case LIT_DEC:
                        case LIT_BIN:
                            return pool.ensureLiteralConstant(
                                    Format.FPLiteral, literal.getString(getSource()));
                        }
                    break;

                // typed literals
                case "Char":
                    if (literal.getId() == Id.LIT_CHAR)
                        {
                        return pool.ensureCharConstant(
                                ((Character) literal.getValue()).charValue());
                        }
                    else if (literal.getId() == Id.LIT_INT)
                        {
                        int nVal = '?';
                        PackedInteger piVal = (PackedInteger) literal.getValue();
                        if (isIntInRange(0, 0x10FFFF))
                            {
                            nVal = piVal.getInt();
                            }
                        else
                            {
                            log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                                    sName, literal.getString(getSource()));
                            }
                        return pool.ensureCharConstant(nVal);
                        }
                    break;

                case "String":
                    switch (literal.getId())
                        {
                        case LIT_CHAR:
                        case LIT_STRING:
                            return pool.ensureStringConstant(literal.getValue().toString());
                        }
                    break;

                case "Bit":
                case "Nibble":
                    if (literal.getId() == Id.LIT_INT)
                        {
                        // create the corresponding IntLiteral
                        // - range of Bit is 0..1
                        // - range of Nibble is 0..F (hexit)
                        boolean fBit = sName.equals("Bit");
                        if (!isIntInRange(0, fBit ? 1 : 0xF))
                            {
                            log(errs, Severity.ERROR, Compiler.VALUE_OUT_OF_RANGE,
                                    sName, literal.getString(getSource()));
                            }
                        Argument argLit = this.generateArgument(code,
                                pool.ensureEcstasyTypeConstant("IntLiteral"), false, errs);

                        // Bit    = IntLiteral.to<Bit>()
                        // Nibble = IntLiteral.to<Nibble>()
                        TypeConstant typeResult = pool.ensureEcstasyTypeConstant(sName);
                        Register argResult = new Register(typeResult);
                        MethodConstant methodTo = pool.ensureMethodConstant(
                                pool.ensureEcstasyClassConstant("IntLiteral"), "to", Access.PUBLIC,
                                new TypeConstant[] {typeResult}, SignatureConstant.NO_TYPES);

                        code.add(new Var(argResult));
                        code.add(new Invoke_01(argLit, methodTo, argResult));
                        return argResult;
                        }
                    break;

                case "Dec32":
                    // TODO
                    break;

                case "Dec64":
                    // TODO
                    break;

                case "Dec128":
                    // TODO
                    break;

                case "VarDec":
                    // TODO
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

                case "Float16":
                    // TODO
                    break;

                case "Float32":
                    switch (literal.getId())
                        {
                        case LIT_INT:
                        case LIT_DEC:
                        case LIT_BIN:           // TODO need to support *purposeful* NaN/infinity
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
                        case LIT_BIN:           // TODO need to support *purposeful* NaN/infinity
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
                    // TODO
                    break;

                case "VarFloat":
                    // TODO
                    break;

                default:
                    // just let it fall through to the generic handling
                    break;
                }
            }

        return validateAndConvertSingle(toConstant(), code, constType, errs);
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
