package org.xvm.javajit.builders;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.math.BigInteger;

import java.util.Collection;

import java.util.function.BiConsumer;

import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitTypeDesc;
import org.xvm.javajit.TypeSystem;

import static java.lang.constant.ConstantDescs.CD_Integer;
import static java.lang.constant.ConstantDescs.CD_Long;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;

/**
 * The builder for IntNumber types.
 */
public class IntNumberBuilder extends NumberBuilder {

    public IntNumberBuilder(TypeSystem typeSystem, TypeSystem.Artifact art, ClassModel model) {
        super(typeSystem, art, model);
    }

    @Override
    protected Collection<PropertyInfo> getProperties() {
        return pool().typeIntNumber().ensureTypeInfo().getProperties().values();
    }

    @Override
    protected BiConsumer<CodeBuilder, JitMethodDesc> getPropertyCodeGenerator(String jitName) {
        return switch (jitName) {
            case "leftmostBit"             -> this::generateLeftmostBitGet;
            case "rightmostBit"            -> this::generateRightmostBitGet;
            case "leadingZeroCount"        -> this::generateLeadingZeroCountGet;
            case "trailingZeroCount"       -> this::generateTrailingZeroCountGet;
            default -> super.getPropertyCodeGenerator(jitName);
        };
    }

    @Override
    protected BiConsumer<CodeBuilder, JitMethodDesc> getMethodCodeGenerator(String jitName) {
        return switch (jitName) {
            case "and", "or", "xor" ->
                    (code, jmd) -> generateBinaryBitwise(code, jmd, jitName);
            case "not" -> this::generateNot;
            case "toByte", "toNibble",
                 "toInt",  "toInt8",  "toInt16",  "toInt32",  "toInt64",  "toInt128",
                 "toUInt", "toUInt8", "toUInt16", "toUInt32", "toUInt64", "toUInt128" ->
                    this::generateFixedConversion;
            case "toIntN", "toUIntN" -> this::generateUnboundedConversion;
            default -> super.getMethodCodeGenerator(jitName);
        };
    }

    @Override
    protected boolean useNaturalImplementation(MethodInfo method) {
        String name = method.getJitIdentity().getName();
        return name.equals("appendTo")             ||
               name.equals("estimateStringLength") ||
               name.equals("mul")                  ||
               name.equals("neg")                  ||
               name.equals("parse")                ||
               name.equals("pow")                  ||
               name.equals("toChar")               ||
               super.useNaturalImplementation(method);
    }

    // ----- properties ----------------------------------------------------------------------------

    /**
     * Assemble an optimized static implementation of "leftmostBit$get$p()".
     *
     * {@code return Integer.highestOneBit(value);}
     */
    protected void generateLeftmostBitGet(CodeBuilder code, JitMethodDesc jmd) {
        if (thisType.isJavaPrimitive()) {
            ClassDesc cd   = JitTypeDesc.getJavaPrimitive(thisType);
            int       slot = code.parameterSlot(0);
            assert cd != null;

            switch (cd.descriptorString()) {
                case "I", "S", "B", "Z":
                    code.iload(slot);
                    switch (thisType.getSingleUnderlyingClass(false).getName()) {
                        case "Nibble"          -> code.ldc(0x0F).iand();
                        case "Int8", "UInt8"   -> code.ldc(0xFF).iand();
                        case "Int16", "UInt16" -> code.ldc(0xFFFF).iand();
                    }
                    code.invokestatic(CD_JavaInteger, "highestOneBit",
                                MethodTypeDesc.of(CD_int, CD_int));
                    Builder.adjustIntValue(code, thisType);
                    break;
                case "J":
                    code.lload(slot)
                        .invokestatic(CD_JavaLong, "highestOneBit",
                                MethodTypeDesc.of(CD_long, CD_long));
                    break;
                default:
                    throw new IllegalStateException();
            }
            addPrimitiveReturn(code, jmd);
        } else if (thisType.isXvmPrimitive()) {
            String name = thisType.getSingleUnderlyingClass(false).getName();
            switch (name) {
                case "Int128", "UInt128":
                    int   slotLow  = code.parameterSlot(0);
                    int   slotHigh = code.parameterSlot(1);
                    Label labelLow = code.newLabel();

                    // get the high bits first
                    code.lconst_0()
                        .lload(slotHigh)
                        .dup2()
                        .lconst_0()
                        .lcmp()
                        .ifeq(labelLow)
                        .invokestatic(CD_JavaLong, "highestOneBit",
                                MethodTypeDesc.of(CD_long, CD_long));
                    addPrimitiveReturn(code, jmd);

                    // high value is zero, check the low value
                    code.labelBinding(labelLow)
                        // the long zero and duplicated high bits will be on the stack, pop them
                        .pop2()
                        .pop2()
                        // get and return the highestOneBit for the low value
                        .lload(slotLow)
                        .invokestatic(CD_JavaLong, "highestOneBit",
                            MethodTypeDesc.of(CD_long, CD_long))
                        .lconst_0();
                    addPrimitiveReturn(code, jmd);
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported XVM primitive number "
                            + thisType);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported number type: " + thisType);
        }
    }

    /**
     * Assemble an optimized static implementation of "rightmostBit$get$p()".
     *
     * {@code return Integer.lowestOneBit(value);}
     */
    protected void generateRightmostBitGet(CodeBuilder code, JitMethodDesc jmd) {
        if (thisType.isJavaPrimitive()) {
            ClassDesc cd   = JitTypeDesc.getJavaPrimitive(thisType);
            int       slot = code.parameterSlot(0);
            assert cd != null;

            switch (cd.descriptorString()) {
                case "I", "S", "B", "Z":
                    code.iload(slot)
                        .invokestatic(CD_JavaInteger, "lowestOneBit",
                                MethodTypeDesc.of(CD_int, CD_int));
                    Builder.adjustIntValue(code, thisType);
                    break;
                case "J":
                    code.lload(slot)
                        .invokestatic(CD_JavaLong, "lowestOneBit",
                                MethodTypeDesc.of(CD_long, CD_long));
                    break;
                default:
                    throw new IllegalStateException();
            }
            addPrimitiveReturn(code, jmd);
        } else if (thisType.isXvmPrimitive()) {
            String name = thisType.getSingleUnderlyingClass(false).getName();
            switch (name) {
                case "Int128", "UInt128":
                    int   slotLow  = code.parameterSlot(0);
                    int   slotHigh = code.parameterSlot(1);
                    Label labelHigh = code.newLabel();

                    // get the low bits first
                    code.lload(slotLow)
                        .invokestatic(CD_JavaLong, "lowestOneBit",
                                MethodTypeDesc.of(CD_long, CD_long))
                        .dup2()           // duplicate the low result
                        .lconst_0()       // load zero
                        .lcmp()           // compare result to zero
                        .ifeq(labelHigh); // if zero, do the high value

                        code.lconst_0();
                        addPrimitiveReturn(code, jmd);

                    // duplicated low result on the stack is zero, check the high value
                    code.labelBinding(labelHigh)
                        .lload(slotHigh)
                        .invokestatic(CD_JavaLong, "lowestOneBit",
                                MethodTypeDesc.of(CD_long, CD_long));
                    addPrimitiveReturn(code, jmd);
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported XVM primitive number "
                            + thisType);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported number type: " + thisType);
        }
    }

    /**
     * Assemble an optimized static implementation of "leadingZeroCount$get$p()".
     *
     * {@code return Integer.numberOfLeadingZeros(value, bitLength);}
     */
    protected void generateLeadingZeroCountGet(CodeBuilder code, JitMethodDesc jmd) {
        if (thisType.isJavaPrimitive()) {
            ClassDesc cd = JitTypeDesc.getJavaPrimitive(thisType);
            assert cd != null;

            int slot      = code.parameterSlot(0);
            int bitLength = getBitLength();

            switch (cd.descriptorString()) {
                case "I", "S", "B", "Z":
                    Label labelEnd  = code.newLabel();
                    int   adjust    = 32 - bitLength;
                    code.iload(slot)
                        .invokestatic(CD_JavaInteger, "numberOfLeadingZeros",
                                MethodTypeDesc.of(CD_int, CD_int))
                        .dup()
                        .ifeq(labelEnd)
                        .loadConstant(adjust)
                        .isub()
                        .labelBinding(labelEnd)
                        .i2l()
                        .lreturn();
                    break;
                case "J":
                    code.lload(slot)
                        .invokestatic(CD_JavaLong, "numberOfLeadingZeros",
                                MethodTypeDesc.of(CD_int, CD_long))
                        .i2l()
                        .lreturn();
                    break;
                default:
                    throw new IllegalStateException();
            }
        } else if (thisType.isXvmPrimitive()) {
            String name = thisType.getSingleUnderlyingClass(false).getName();
            switch (name) {
                case "Int128", "UInt128":
                    int   slotLow  = code.parameterSlot(0);
                    int   slotHigh = code.parameterSlot(1);
                    Label labelLow = code.newLabel();

                    // get the high bits first
                    code.lload(slotHigh)
                        .invokestatic(CD_JavaLong, "numberOfLeadingZeros",
                                MethodTypeDesc.of(CD_int, CD_long))
                        .dup()                    // duplicate the hig result
                        .loadConstant(64)         // compare result to 64
                        .if_icmpeq(labelLow)      // if result is 64, do the low value
                        .i2l()                    // else convert the result to a long (Int64)
                        .lreturn()                // and return
                        // duplicated high result on the stack is 64, calculate the low value
                        .labelBinding(labelLow)
                        .lload(slotLow)
                        .invokestatic(CD_JavaLong, "numberOfLeadingZeros",
                                MethodTypeDesc.of(CD_int, CD_long))
                        .iadd()     // high result (64) and low result are on the stack, add them
                        .i2l()      // convert to long and return
                        .lreturn();
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported XVM primitive number "
                            + thisType);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported number type: " + thisType);
        }
    }

    /**
     * Assemble an optimized static implementation of "trailingZeroCount$get$p()".
     *
     * {@code return Integer.numberOfTrailingZeros(value, bitLength);}
     */
    protected void generateTrailingZeroCountGet(CodeBuilder code, JitMethodDesc jmd) {
        if (thisType.isJavaPrimitive()) {
            ClassDesc cd   = JitTypeDesc.getJavaPrimitive(thisType);
            assert cd != null;

            int bitLength = getBitLength();
            int slot      = code.parameterSlot(0);

            switch (cd.descriptorString()) {
                case "I", "S", "B", "Z":
                    Label labelEnd  = code.newLabel();
                    code.iload(slot)
                        .invokestatic(CD_JavaInteger, "numberOfTrailingZeros",
                                MethodTypeDesc.of(CD_int, CD_int))
                        .dup()
                        .loadConstant(32)
                        .if_icmpne(labelEnd)
                        .pop()
                        .loadConstant(bitLength)
                        .labelBinding(labelEnd)
                        .i2l()
                        .lreturn();
                    break;
                case "J":
                    code.lload(slot)
                        .invokestatic(CD_JavaLong, "numberOfTrailingZeros",
                                MethodTypeDesc.of(CD_int, CD_long))
                        .i2l()
                        .lreturn();
                    break;
                default:
                    throw new IllegalStateException();
            }
        } else if (thisType.isXvmPrimitive()) {
            String name = thisType.getSingleUnderlyingClass(false).getName();
            switch (name) {
                case "Int128", "UInt128":
                    int   slotLow  = code.parameterSlot(0);
                    int   slotHigh = code.parameterSlot(1);
                    Label labelHigh = code.newLabel();

                    // get the low bits first
                    code.lload(slotLow)
                        .invokestatic(CD_JavaLong, "numberOfTrailingZeros",
                                MethodTypeDesc.of(CD_int, CD_long))
                        .dup()                    // duplicate the low result
                        .loadConstant(64)   // compare result to 64
                        .if_icmpeq(labelHigh)     // if result is 64, do the high value
                        .i2l()                    // else convert the result to a long (Int64)
                        .lreturn()                // and return
                        // duplicated low result on the stack is 64, calculate the high value
                        .labelBinding(labelHigh)
                        .lload(slotHigh)
                        .invokestatic(CD_JavaLong, "numberOfTrailingZeros",
                                MethodTypeDesc.of(CD_int, CD_long))
                        .iadd()     // low result (64) and high result are on the stack, add them
                        .i2l()      // convert to long and return
                        .lreturn();
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported XVM primitive number "
                            + thisType);
            }
        } else {
            throw new UnsupportedOperationException("Unsupported number type: " + thisType);
        }
    }

    // ----- methods -------------------------------------------------------------------------------

    /**
     * Assemble an optimized static implementation of "toIntN$p()" or "toUIntN$p()".
     *
     * {@code return Target.$box(value);}
     */
    protected void generateUnboundedConversion(CodeBuilder code, JitMethodDesc jmd) {
        assert jmd.optimizedReturns.length == 1;

        FixedInt     source         = FixedInt.of(thisType);
        TypeConstant targetType     = jmd.optimizedReturns[0].type;
        boolean      targetUnsigned = targetType.isA(pool().typeUIntN());
        ClassDesc    targetCD       = jmd.optimizedReturns[0].cd;

        assert targetUnsigned || targetType.isA(pool().typeIntN());

        if (targetUnsigned && source.signed) {
            Label valid = code.newLabel();
            if (source.bitLength == 128) {
                code.lload(code.parameterSlot(1))
                    .lconst_0()
                    .lcmp()
                    .ifge(valid);
            } else {
                loadComparableLong(code, source);
                code.lconst_0()
                    .lcmp()
                    .ifge(valid);
            }
            throwOutOfBounds(code, "", code.parameterSlot(jmd.optimizedCtx()));
            code.labelBinding(valid);
        }

        if (source.bitLength == 128) {
            code.lload(code.parameterSlot(0))
                .lload(code.parameterSlot(1))
                .invokestatic(art.CD(), "$toBigInteger",
                        MethodTypeDesc.of(CD_BigInteger, CD_long, CD_long))
                .invokestatic(targetCD, "$box",
                        MethodTypeDesc.of(targetCD, CD_BigInteger));
        } else if (!source.signed && source.bitLength == 64) {
            generateUnsignedLongAsBigInteger(code);
            code.invokestatic(targetCD, "$box",
                    MethodTypeDesc.of(targetCD, CD_BigInteger));
        } else {
            loadConversionLong(code, source, source.signed);
            code.invokestatic(targetCD, "$box", MethodTypeDesc.of(targetCD, CD_long));
        }
        code.areturn();
    }

    /**
     * Convert the unsigned long value to a positive {@link BigInteger}.
     */
    private void generateUnsignedLongAsBigInteger(CodeBuilder code) {
        int   valueSlot   = code.parameterSlot(0);
        Label nonNegative = code.newLabel();

        code.lload(valueSlot)
            .loadConstant(Long.MAX_VALUE)
            .land()
            .invokestatic(CD_BigInteger, "valueOf",
                    MethodTypeDesc.of(CD_BigInteger, CD_long))
            .lload(valueSlot)
            .lconst_0()
            .lcmp()
            .ifge(nonNegative)
            .loadConstant(63)
            .invokevirtual(CD_BigInteger, "setBit",
                    MethodTypeDesc.of(CD_BigInteger, CD_int))
            .labelBinding(nonNegative);
    }

    /**
     * Assemble an optimized static fixed-length integer conversion.
     *
     * {@code return checkBounds ? checkedConvert(value) : truncate(value);}
     */
    protected void generateFixedConversion(CodeBuilder code, JitMethodDesc jmd) {
        assert jmd.optimizedReturns.length > 0;

        FixedInt source = FixedInt.of(thisType);
        FixedInt target = FixedInt.of(jmd.optimizedReturns[0].type);

        if (needsLowerCheck(source, target) || needsUpperCheck(source, target)) {
            int[] checkParams = jmd.getAllOptimizedParams(0);
            assert checkParams.length == 2;
            assert !jmd.optimizedParams[checkParams[0]].extension;
            assert jmd.optimizedParams[checkParams[1]].extension;

            int   adjust       = jmd.getImplicitParamCount();
            int   checkSlot    = code.parameterSlot(checkParams[0] + adjust);
            int   defaultSlot  = code.parameterSlot(checkParams[1] + adjust);
            Label convert      = code.newLabel();
            Label outOfBounds  = code.newLabel();

            code.iload(defaultSlot)
                .ifne(convert)
                .iload(checkSlot)
                .ifeq(convert);

            generateBoundsCheck(code, source, target, outOfBounds);
            code.goto_(convert)
                .labelBinding(outOfBounds);
            throwOutOfBounds(code, "", code.parameterSlot(jmd.optimizedCtx()));
            code.labelBinding(convert);
        }

        generateConversion(code, source, target);
        addPrimitiveReturn(code, jmd);
    }

    /**
     * Branch to the specified label if the source value is outside of the target range.
     */
    private void generateBoundsCheck(
            CodeBuilder code, FixedInt source, FixedInt target, Label outOfBounds) {
        boolean checkLower = needsLowerCheck(source, target);
        boolean checkUpper = needsUpperCheck(source, target);

        if (source.bitLength == 128) {
            if (checkLower) {
                generate128Compare(code, target, true);
                code.iflt(outOfBounds);
            }
            if (checkUpper) {
                generate128Compare(code, target, false);
                code.ifgt(outOfBounds);
            }
        } else {
            if (checkLower) {
                loadComparableLong(code, source);
                code.loadConstant(target.lowerLow())
                    .lcmp()
                    .iflt(outOfBounds);
            }
            if (checkUpper) {
                loadComparableLong(code, source);
                code.loadConstant(target.upperLow());
                if (!source.signed && source.bitLength == 64) {
                    code.invokestatic(CD_Long, "compareUnsigned",
                            MethodTypeDesc.of(CD_int, CD_long, CD_long));
                } else {
                    code.lcmp();
                }
                code.ifgt(outOfBounds);
            }
        }
    }

    /**
     * Compare the 128-bit source value to the specified bound.
     */
    private void generate128Compare(CodeBuilder code, FixedInt target, boolean lower) {
        code.lload(code.parameterSlot(0))
            .lload(code.parameterSlot(1))
            .loadConstant(lower ? target.lowerLow()  : target.upperLow())
            .loadConstant(lower ? target.lowerHigh() : target.upperHigh())
            .invokestatic(art.CD(), "$compare",
                    MethodTypeDesc.of(CD_int, CD_long, CD_long, CD_long, CD_long));
    }

    /**
     * Load a source value no wider than 64 bits as a numerically equivalent Java long.
     */
    private void loadComparableLong(CodeBuilder code, FixedInt source) {
        if (source.bitLength <= 32) {
            code.iload(code.parameterSlot(0));
            Builder.adjustIntValue(code, thisType);
            if (!source.signed && source.bitLength == 32) {
                code.invokestatic(CD_Integer, "toUnsignedLong",
                        MethodTypeDesc.of(CD_long, CD_int));
            } else {
                code.i2l();
            }
        } else {
            code.lload(code.parameterSlot(0));
        }
    }

    /**
     * Load the low 64 bits of the source, extending values narrower than 64 bits as requested.
     */
    private void loadConversionLong(CodeBuilder code, FixedInt source, boolean signExtend) {
        if (source.bitLength <= 32) {
            code.iload(code.parameterSlot(0));
            Builder.adjustIntValue(code, thisType);
            if (signExtend) {
                code.i2l();
            } else if (source.bitLength == 32) {
                code.invokestatic(CD_Integer, "toUnsignedLong",
                        MethodTypeDesc.of(CD_long, CD_int));
            } else {
                code.loadConstant((1 << source.bitLength) - 1)
                    .iand()
                    .i2l();
            }
        } else {
            code.lload(code.parameterSlot(0));
        }
    }

    /**
     * Convert the source value to the target representation and leave its slots on the stack.
     */
    private void generateConversion(CodeBuilder code, FixedInt source, FixedInt target) {
        boolean signExtend = source.signed;

        if (target.bitLength <= 32) {
            if (source.bitLength <= 32) {
                code.iload(code.parameterSlot(0));
            } else {
                code.lload(code.parameterSlot(0))
                    .l2i();
            }
            Builder.adjustIntValue(code, target.type);
        } else if (target.bitLength == 64) {
            if (source.bitLength == 128) {
                code.lload(code.parameterSlot(0));
            } else {
                loadConversionLong(code, source, signExtend);
            }
        } else {
            assert target.bitLength == 128;

            if (source.bitLength == 128) {
                code.lload(code.parameterSlot(0))
                    .lload(code.parameterSlot(1));
            } else {
                loadConversionLong(code, source, signExtend);
                if (signExtend) {
                    if (source.bitLength <= 32) {
                        code.iload(code.parameterSlot(0))
                            .i2l();
                    } else {
                        code.lload(code.parameterSlot(0));
                    }
                    code.loadConstant(63)
                        .lshr();
                } else {
                    code.lconst_0();
                }
            }
        }
    }

    /**
     * Determine whether the source range extends below the target range.
     */
    private static boolean needsLowerCheck(FixedInt source, FixedInt target) {
        return source.signed && (!target.signed || source.bitLength > target.bitLength);
    }

    /**
     * Determine whether the source range extends above the target range.
     */
    private static boolean needsUpperCheck(FixedInt source, FixedInt target) {
        return source.signed
                ? target.signed
                    ? source.bitLength > target.bitLength
                    : source.bitLength - 1 > target.bitLength
                : target.signed
                    ? source.bitLength >= target.bitLength
                    : source.bitLength > target.bitLength;
    }

    /**
     * The signedness and bit length of a fixed-length integer type.
     */
    private record FixedInt(TypeConstant type, int bitLength, boolean signed) {
        private static FixedInt of(TypeConstant type) {
            String name = type.getSingleUnderlyingClass(false).getName();
            return switch (name) {
                case "Bit"     -> new FixedInt(type,   1, false);
                case "Nibble"  -> new FixedInt(type,   4, false);
                case "Int8"    -> new FixedInt(type,   8, true );
                case "UInt8"   -> new FixedInt(type,   8, false);
                case "Int16"   -> new FixedInt(type,  16, true );
                case "UInt16"  -> new FixedInt(type,  16, false);
                case "Int32"   -> new FixedInt(type,  32, true );
                case "UInt32"  -> new FixedInt(type,  32, false);
                case "Int64"   -> new FixedInt(type,  64, true );
                case "UInt64"  -> new FixedInt(type,  64, false);
                case "Int128"  -> new FixedInt(type, 128, true );
                case "UInt128" -> new FixedInt(type, 128, false);
                default -> throw new IllegalArgumentException(
                        "Not a fixed-length integer: " + type);
            };
        }

        private long lowerLow() {
            if (!signed) {
                return 0L;
            }
            if (bitLength == 128) {
                return 0L;
            }
            return bitLength == 64 ? Long.MIN_VALUE : -(1L << bitLength - 1);
        }

        private long lowerHigh() {
            if (!signed) {
                return 0L;
            }
            return bitLength == 128 ? Long.MIN_VALUE : -1L;
        }

        private long upperLow() {
            if (bitLength == 128) {
                return -1L;
            }
            if (bitLength == 64) {
                return signed ? Long.MAX_VALUE : -1L;
            }
            int magnitudeBits = signed ? bitLength - 1 : bitLength;
            return (1L << magnitudeBits) - 1;
        }

        private long upperHigh() {
            if (bitLength == 128) {
                return signed ? Long.MAX_VALUE : -1L;
            }
            return 0L;
        }
    }

    /**
     * Assemble optimized static implementations of "and$p(IntNumber)", "or$p(IntNumber)", and
     * "xor$p(IntNumber)".
     *
     * {@code and(that) -> value & that;}
     * {@code or(that)  -> value | that;}
     * {@code xor(that) -> value ^ that;}
     */
    protected void generateBinaryBitwise(CodeBuilder code, JitMethodDesc jmd, String jitName) {
        assert jmd.optimizedMD.returnType().isPrimitive();

        int valueCount = jmd.optimizedCtx();
        int thatIndex  = jmd.getOptimizedParamIndex(0);
        for (int i = 0; i < valueCount; i++) {
            ClassDesc valueCD = jmd.optimizedParams[i].cd;
            assert valueCD.equals(jmd.optimizedParams[thatIndex + i].cd);

            load(code, valueCD, code.parameterSlot(i));
            load(code, valueCD,
                    code.parameterSlot(jmd.getImplicitParamCount() + thatIndex + i));

            boolean wide = Builder.toTypeKind(valueCD).slotSize() == 2;
            switch (jitName) {
            case "and":
                if (wide) {
                    code.land();
                } else {
                    code.iand();
                }
                break;

            case "or":
                if (wide) {
                    code.lor();
                } else {
                    code.ior();
                }
                break;

            case "xor":
                if (wide) {
                    code.lxor();
                } else {
                    code.ixor();
                }
                break;

            default:
                throw new IllegalArgumentException(jitName);
            }
        }

        addPrimitiveReturn(code, jmd);
    }

    /**
     * Assemble an optimized static implementation of "not$p()".
     *
     * {@code return ~value;}
     */
    protected void generateNot(CodeBuilder code, JitMethodDesc jmd) {
        assert jmd.optimizedMD.returnType().isPrimitive();

        int valueCount = jmd.optimizedCtx();
        for (int i = 0; i < valueCount; i++) {
            ClassDesc valueCD = jmd.optimizedParams[i].cd;
            load(code, valueCD, code.parameterSlot(i));
            if (Builder.toTypeKind(valueCD).slotSize() == 2) {
                code.ldc(-1L)
                    .lxor();
            } else {
                code.iconst_m1()
                    .ixor();
                Builder.adjustIntValue(code, thisType);
            }
        }

        addPrimitiveReturn(code, jmd);
    }

    private static final ClassDesc CD_BigInteger = ClassDesc.of(BigInteger.class.getName());
}
