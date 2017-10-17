package org.xvm.compiler.ast;


import java.math.BigDecimal;

import java.util.List;

import org.xvm.asm.Constant;
import org.xvm.asm.Constant.Format;
import org.xvm.asm.ConstantPool;
import org.xvm.asm.Constants.Access;
import org.xvm.asm.MethodStructure.Code;
import org.xvm.asm.Op.Argument;

import org.xvm.asm.constants.AccessTypeConstant;
import org.xvm.asm.constants.ClassConstant;
import org.xvm.asm.constants.DecimalConstant;
import org.xvm.asm.constants.DifferenceTypeConstant;
import org.xvm.asm.constants.Float16Constant;
import org.xvm.asm.constants.ImmutableTypeConstant;
import org.xvm.asm.constants.IntersectionTypeConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.ParameterizedTypeConstant;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.asm.constants.UnionTypeConstant;
import org.xvm.asm.op.Invoke_01;
import org.xvm.asm.op.Var;

import org.xvm.compiler.Compiler;
import org.xvm.compiler.ErrorListener;
import org.xvm.compiler.Token;
import org.xvm.compiler.Token.Id;

import org.xvm.type.Decimal;
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
                BigDecimal bigdec = (BigDecimal) literal.getValue();
                try
                    {
                    return pool.ensureDecimalConstant(new Decimal64(bigdec));
                    }
                catch (ArithmeticException e) {}
                try
                    {
                    return pool.ensureDecimalConstant(new Decimal128(bigdec));
                    }
                catch (ArithmeticException e) {}
                return toVarLenDecimalConstant(bigdec);

            case LIT_BIN:
                // TODO this should first try 64-bit, then 128-bit, then use the var-len format
                return pool.ensureFloat64Constant(Double.parseDouble(literal.getValue().toString()));

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

    private DecimalConstant toVarLenDecimalConstant(BigDecimal bigdec)
        {
        throw new UnsupportedOperationException("var-len decimal not implemented");
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
    public boolean isConstant()
        {
        return true;
        }

    @Override
    public TypeConstant getImplicitType()
        {
        return toConstant().getType();
        }

    @Override
    public boolean isAssignableTo(TypeConstant typeThat)
        {
        if (typeThat instanceof ImmutableTypeConstant)
            {
            // all of the literal expression arguments are "const" objects, so immutable is OK
            return isAssignableTo(typeThat.getUnderlyingType());
            }

        if (typeThat.isSingleDefiningConstant()
                && typeThat.getDefiningConstant() instanceof ClassConstant
                && ((ClassConstant) typeThat.getDefiningConstant()).getModuleConstant().isEcstasyModule()
                && typeThat.getAccess() == Access.PUBLIC)
            {
            Id            id    = literal.getId();
            PackedInteger piVal;
            String        sName = ((ClassConstant) typeThat.getDefiningConstant()).getPathString();
            switch (sName)
                {
                // these are all of the super-classes and interfaces that could be represented by
                // a literal expression
                case "Object":
                case "Const":
                case "Orderable":
                case "collections.Hashable":
                    return true;

                case "Sequential":
                    // char and the various ints are sequential
                    return id == Id.LIT_CHAR || id == Id.LIT_INT;

                case "Char":
                    // char and the various ints are sequential
                    return id == Id.LIT_CHAR || id == Id.LIT_INT && isIntInRange(0, 0x10FFFF);

                case "String":
                    return id == Id.LIT_CHAR || id == Id.LIT_STRING;

                case "Number":
                case "FPNumber":
                case "FPLiteral":
                case "BinaryFPNumber":
                case "VarFloat":
                    return id == Id.LIT_INT || id == Id.LIT_DEC || id == Id.LIT_BIN;

                case "IntNumber":
                case "IntLiteral":
                case "VarInt":
                case "annotations.UncheckedInt":
                    return id == Id.LIT_INT;

                case "UIntNumber":
                case "VarUInt":
                    // unsigned value must be >= 0
                    return id == Id.LIT_INT && !((PackedInteger) literal.getValue()).isNegative();

                case "DecimalFPNumber":
                case "VarDec":
                    return id == Id.LIT_INT || id == Id.LIT_DEC;

                case "Bit":
                    return isIntInRange(0, 1);

                case "Nibble":
                    return isIntInRange(0, 0xF);

                case "Int8":
                    return isIntInRange(Byte.MIN_VALUE, Byte.MAX_VALUE);

                case "Int16":
                    return isIntInRange(Short.MIN_VALUE, Short.MAX_VALUE);

                case "Int32":
                    return isIntInRange(Integer.MIN_VALUE, Integer.MAX_VALUE);

                case "Int64":
                    return isIntInRange(Long.MIN_VALUE, Long.MAX_VALUE);

                case "Int128":
                    return id == Id.LIT_INT && ((PackedInteger) literal.getValue()).getSignedByteSize() <= 16;

                case "UInt8":
                    return isIntInRange(0x00, 0xFF);

                case "UInt16":
                    return isIntInRange(0x0000, 0xFFFF);

                case "UInt32":
                    return isIntInRange(0x00000000L, 0xFFFFFFFFL);

                case "UInt64":
                    return id == Id.LIT_INT
                            && !(piVal = ((PackedInteger) literal.getValue())).isNegative()
                            && piVal.getUnsignedByteSize() <= 8;

                case "UInt128":
                    return id == Id.LIT_INT
                            && !(piVal = ((PackedInteger) literal.getValue())).isNegative()
                            && piVal.getUnsignedByteSize() <= 16;

                case "Dec32":
                    if (id == Id.LIT_INT || id == Id.LIT_DEC)
                        {
                        BigDecimal bigdec = id == Id.LIT_INT
                                ? new BigDecimal(((PackedInteger) literal.getValue()).getBigInteger())
                                : (BigDecimal) literal.getValue();
                        try
                            {
                            return new Decimal32(bigdec).isFinite();
                            }
                        catch (ArithmeticException e) {}
                        }
                    return false;

                case "Dec64":
                    if (id == Id.LIT_INT || id == Id.LIT_DEC)
                        {
                        BigDecimal bigdec = id == Id.LIT_INT
                                ? new BigDecimal(((PackedInteger) literal.getValue()).getBigInteger())
                                : (BigDecimal) literal.getValue();
                        try
                            {
                            return new Decimal64(bigdec).isFinite();
                            }
                        catch (ArithmeticException e) {}
                        }
                    return false;

                case "Dec128":
                    if (id == Id.LIT_INT || id == Id.LIT_DEC)
                        {
                        BigDecimal bigdec = id == Id.LIT_INT
                                ? new BigDecimal(((PackedInteger) literal.getValue()).getBigInteger())
                                : (BigDecimal) literal.getValue();
                        try
                            {
                            return new Decimal128(bigdec).isFinite();
                            }
                        catch (ArithmeticException e) {}
                        }
                    return false;

                case "Float16":
                    if (id == Id.LIT_INT || id == Id.LIT_DEC || id == Id.LIT_BIN)
                        {
                        try
                            {
                            // convert to/from 16-bit to test for overflow/underflow
                            Float flVal = Float.parseFloat(literal.getValue().toString());
                            return Float.isFinite(flVal) && Float.isFinite(
                                    Float16Constant.toFloat(Float16Constant.toHalf(flVal)));
                            }
                        catch (RuntimeException e) {}
                        }
                    return false;

                case "Float32":
                    if (id == Id.LIT_INT || id == Id.LIT_DEC || id == Id.LIT_BIN)
                        {
                        try
                            {
                            // TODO add support for *purposeful* NaN/infinity
                            return Float.isFinite(Float.parseFloat(literal.getValue().toString()));
                            }
                        catch (RuntimeException e) {}
                        }
                    return false;

                case "Float64":
                    if (id == Id.LIT_INT || id == Id.LIT_DEC || id == Id.LIT_BIN)
                        {
                        try
                            {
                            // TODO add support for *purposeful* NaN/infinity
                            return Double.isFinite(Double.parseDouble(literal.getValue().toString()));
                            }
                        catch (RuntimeException e) {}
                        }
                    return false;

                case "Float128":
                    // TODO - how to support 128-bit float?
                    throw new UnsupportedOperationException("128-bit binary floating point not implemented");
                }
            }

        return super.isAssignableTo(typeThat);
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
                    switch (literal.getId())
                        {
                        case LIT_INT:
                        case LIT_DEC:
                            return generateArgument(code, pool.ensureEcstasyTypeConstant(
                                    "DecimalFPNumber"), fTupleOk, errs);

                        case LIT_BIN:
                            return generateArgument(code, pool.ensureEcstasyTypeConstant(
                                    "BinaryFPNumber"), fTupleOk, errs);
                        }
                    break;

                case "DecimalFPNumber":
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
                            try
                                {
                                return pool.ensureDecimalConstant(new Decimal128(bigdec));
                                }
                            catch (ArithmeticException e) {}
                            return toVarLenDecimalConstant(bigdec);
                        }
                    break;

                case "BinaryFPNumber":
                    // TODO the idea is the same as int: 64-bit if it fits, otherwise 128-bit, otherwise var-len
                    return generateArgument(code, pool.ensureEcstasyTypeConstant("Float64"), fTupleOk, errs);

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
                            typeTuple, new TypeConstant[] {pool.ensureThisTypeConstant(
                                    pool.ensureEcstasyClassConstant("Object"), null)});
                    TypeConstant   typeFn      = pool.ensureParameterizedTypeConstant(
                            pool.ensureEcstasyTypeConstant("Function"),
                            new TypeConstant[] {typeTParams, typeTReturn});
                    MethodConstant methodTo    = pool.ensureMethodConstant(
                            pool.ensureEcstasyClassConstant("Object"), "to", Access.PUBLIC,
                            SignatureConstant.NO_TYPES, new TypeConstant[] {typeFn});

                    // generate the code that turns the constant value from this expression into a
                    // function object that returns that value
                    Var varResult = new Var(typeFn);
                    code.add(varResult);
                    code.add(new Invoke_01(argVal, methodTo, varResult.getRegister()));
                    return varResult.getRegister();
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
                        MethodConstant methodTo = pool.ensureMethodConstant(
                            pool.ensureEcstasyClassConstant("IntLiteral"), "to", Access.PUBLIC,
                            SignatureConstant.NO_TYPES, new TypeConstant[]{typeResult});

                        Var varResult = new Var(typeResult);
                        code.add(varResult);
                        code.add(new Invoke_01(argLit, methodTo, varResult.getRegister()));
                        return varResult.getRegister();
                        }
                    break;

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
                            return toVarLenDecimalConstant(bigdec);
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
                    // TODO - how to support 128-bit float?
                    throw new UnsupportedOperationException("128-bit binary floating point not implemented");

                case "VarFloat":
                    // TODO - how to support var-len float?
                    throw new UnsupportedOperationException("var-len binary floating point not implemented");

                default:
                    // just let it fall through to the generic handling
                    break;
                }
            }

        return validateAndConvertSingle(toConstant(), code, constType, errs);
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
