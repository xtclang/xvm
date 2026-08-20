package org.xvm.javajit.builders;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.Collection;

import java.util.function.BiConsumer;

import org.xvm.asm.constants.PropertyInfo;

import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitTypeDesc;
import org.xvm.javajit.TypeSystem;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_double;
import static java.lang.constant.ConstantDescs.CD_float;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

/**
 * The builder for FPNumber types.
 */
public class FPNumberBuilder extends NumberBuilder {

    public FPNumberBuilder(TypeSystem typeSystem, TypeSystem.Artifact art, ClassModel model) {
        super(typeSystem, art, model);
    }

    protected Collection<PropertyInfo> getProperties() {
        return pool().typeFPNumber().ensureTypeInfo().getProperties().values();
    }

    @Override
    protected BiConsumer<CodeBuilder, JitMethodDesc> getPropertyCodeGenerator(String jitName) {
        return switch (jitName) {
            case "exponent"             -> this::generateExponentGet;
            case "exponentBitLength"    -> this::generateExponentBitLengthGet;
            case "precision"            -> this::generatePrecisionGet;
            case "radix"                -> this::generateRadixGet;
            case "significand"          -> this::generateSignificandGet;
            case "significandBitLength" -> this::generateSignificandBitLengthGet;
            default -> super.getPropertyCodeGenerator(jitName);
        };
    }

    @Override
    protected BiConsumer<CodeBuilder, JitMethodDesc> getMethodCodeGenerator(String jitName) {
        return switch (jitName) {
            case "round" -> this::generateRound;
            case "floor" -> this::generateFloor;
            case "ceil"  -> this::generateCeil;
            default      -> super.getMethodCodeGenerator(jitName);
        };
    }

    // ----- properties ----------------------------------------------------------------------------

    // TODO - this can probably be removed after constructor/primitive-conversion path is fixed

    /**
     * Assemble an optimized static implementation of "exponentBitLength$get$p()".
     */
    protected void generateExponentBitLengthGet(CodeBuilder code, JitMethodDesc jmd) {
        long exp = getExponentLength();
        code.loadConstant(exp)
            .lreturn();
    }

    /**
     * Assemble an optimized static implementation of "precision$get$p()".
     */
    protected void generatePrecisionGet(CodeBuilder code, JitMethodDesc jmd) {
        long p = getSignificandLength() + 1;
        code.loadConstant(p)
            .lreturn();
    }

    /**
     * Assemble an optimized static implementation of "significandBitLength$get$p()".
     */
    protected void generateSignificandBitLengthGet(CodeBuilder code, JitMethodDesc jmd) {
        long p = getSignificandLength();
        code.loadConstant(p)
            .lreturn();
    }

    /**
     * Assemble an optimized static implementation of "exponent$get$p()".
     *
     * {@code return Int64.valueOf((rawBits & exponentMask) >>> significandBitLength);}
     */
    protected void generateExponentGet(CodeBuilder code, JitMethodDesc jmd) {
        generateGetExponent(code, jmd);
        code.areturn();
    }

    /**
     * Assemble an optimized static implementation of "significand$get$p()".
     *
     * {@code return Int64.valueOf(rawBits & significandMask);}
     */
    protected void generateSignificandGet(CodeBuilder code, JitMethodDesc jmd) {
        generateGetSignificand(code, jmd);
        code.areturn();
    }

    /**
     * @return the IEEE 754-2008 significand length for this type
     */
    protected int getSignificandLength() {
        return getBitLength() - getExponentLength() - 1;
    }

    /**
     * @return the IEEE 754-2008 exponent length for this type
     */
    protected int getExponentLength() {
        String name      = thisType.getSingleUnderlyingClass(false).getName();
        int    bitLength = getBitLength();

        return switch (name) {
            case "Float8e4"                     -> 4;
            case "Float8e5", "Float16"          -> 5;
            case "Dec32"                        -> 6;
            case "BFloat16", "Float32", "Dec64" -> 8;
            case "Float64"                      -> 11;
            case "Dec128"                       -> 12;
            default -> switch (bitLength) {
                case 16  -> 5;
                case 32  -> 8;
                case 64  -> 11;
                case 128 -> 15;
                case 256 -> 19;
                default  -> {
                    if (thisType.isA(pool().typeBinFPNumber()) && bitLength >= 128) {
                        // IEEE-754-2008 spec Table 3.5 — Binary interchange format parameters
                        // for bit lengths >= 128
                        yield (int) Math.round((Math.log10(bitLength) / Math.log10(2)) * 4) - 13;
                    } else if (thisType.isA(pool().typeDecFPNumber()) && bitLength >= 32) {
                        // IEEE-754-2008 spec Table 3.6 — Decimal interchange format parameters
                        // for bit lengths >= 32
                        yield (bitLength / 16) + 9;
                    }
                    throw new UnsupportedOperationException("Unsupported bitLength " + bitLength);
                }
            };
        };
    }

    /**
     * Assemble an optimized static implementation of "radix$get$p()".
     */
    protected void generateRadixGet(CodeBuilder code, JitMethodDesc jmd) {
        long radix = thisType.isA(pool().typeBinFPNumber()) ? 2L : 10L;
        code.loadConstant(radix)
            .lreturn();
    }

    /**
     * Generate code to calculate the exponent and leave it on the stack
     */
    protected void generateGetExponent(CodeBuilder code, JitMethodDesc jmd) {
        int sigLen = getSignificandLength();

        if (thisType.isJavaPrimitive()) {
            ClassDesc cd   = JitTypeDesc.getJavaPrimitive(thisType);
            int       slot = code.parameterSlot(0);
            assert cd != null;

            switch (cd.descriptorString()) {
                case "I", "S", "B", "Z":
                code.iload(slot)
                    .loadConstant(Integer.MAX_VALUE)
                    .iand()
                    .loadConstant(sigLen)
                    .iushr()
                    .i2l();
                break;
            case "J":
                code.lload(slot)
                    .loadConstant(Long.MAX_VALUE)
                    .land()
                    .loadConstant(sigLen)
                    .lushr();
                break;
            case "F":
                code.fload(slot)
                    .invokestatic(CD_JavaFloat, "floatToRawIntBits",
                            MethodTypeDesc.of(CD_int, CD_float))
                    .loadConstant(Integer.MAX_VALUE)
                    .iand()
                    .loadConstant(sigLen)
                    .iushr()
                    .i2l();
                break;
            case "D":
                code.dload(slot)
                    .invokestatic(CD_JavaDouble, "doubleToRawLongBits",
                            MethodTypeDesc.of(CD_long, CD_double))
                    .loadConstant(Long.MAX_VALUE)
                    .land()
                    .loadConstant(sigLen)
                    .lushr();
                break;
            default:
                throw new IllegalStateException();
            }
            box(code, pool().typeInt64());
        } else {
            throwIllegalState(code, "Not Implemented",
                    code.parameterSlot(jmd.optimizedMD.parameterCount() - 1));
        }
    }

    /**
     * Generate code to calculate the significand and leave it on the stack
     */
    protected void generateGetSignificand(CodeBuilder code, JitMethodDesc jmd) {
        long bitLen = getBitLength();
        long sigLen = getSignificandLength();
        int  shift  = (int) (bitLen - sigLen);

        if (thisType.isJavaPrimitive()) {
            ClassDesc cd   = JitTypeDesc.getJavaPrimitive(thisType);
            int       slot = code.parameterSlot(0);
            assert cd != null;

            switch (cd.descriptorString()) {
                case "I", "S", "B", "Z":
                code.iload(slot)
                    .loadConstant(shift)
                    .ishl()
                    .loadConstant(shift)
                    .iushr()
                    .i2l();
                break;
            case "J":
                code.lload(slot)
                    .loadConstant(shift)
                    .lshl()
                    .loadConstant(shift)
                    .lushr();
                break;
            case "F":
                code.fload(slot)
                    .invokestatic(CD_JavaFloat, "floatToRawIntBits",
                            MethodTypeDesc.of(CD_int, CD_float))
                    .loadConstant(shift)
                    .ishl()
                    .loadConstant(shift)
                    .iushr()
                    .i2l();
                break;
            case "D":
                code.dload(slot)
                    .invokestatic(CD_JavaDouble, "doubleToRawLongBits",
                            MethodTypeDesc.of(CD_long, CD_double))
                    .loadConstant(shift)
                    .lshl()
                    .loadConstant(shift)
                    .lushr();
                break;
            default:
                throw new IllegalStateException();
            }
            box(code, pool().typeInt64());
        } else {
            throwIllegalState(code, "Not Implemented",
                    code.parameterSlot(jmd.optimizedMD.parameterCount() - 1));
        }
    }

    // ----- methods -------------------------------------------------------------------------------

    /**
     * Assemble an optimized static implementation of "round$p()".
     *
     * {@code return finite ? bigDecimal.setScale(0, direction).toFPNumber() : this;}
     */
    protected void generateRound(CodeBuilder code, JitMethodDesc jmd) {
        generateRounding(code, jmd, null);
    }

    /**
     * Assemble an optimized static implementation of "floor$p()".
     *
     * {@code return finite ? bigDecimal.setScale(0, FLOOR).toFPNumber() : this;}
     */
    protected void generateFloor(CodeBuilder code, JitMethodDesc jmd) {
        generateRounding(code, jmd, "FLOOR");
    }

    /**
     * Assemble an optimized static implementation of "ceil$p()".
     *
     * {@code return finite ? bigDecimal.setScale(0, CEILING).toFPNumber() : this;}
     */
    protected void generateCeil(CodeBuilder code, JitMethodDesc jmd) {
        generateRounding(code, jmd, "CEILING");
    }

    /**
     * Generate a rounding operation for a primitive FPNumber.
     *
     * @param mode  the fixed {@link java.math.RoundingMode} name, or null to load the method
     *              argument
     */
    protected void generateRounding(CodeBuilder code, JitMethodDesc jmd, String mode) {
        if (thisType.isJavaPrimitive()) {
            generateBinaryRounding(code, jmd, mode);
        } else if (thisType.isXvmPrimitive()) {
            generateDecimalRounding(code, jmd, mode);
        } else {
            throw new IllegalStateException("Unsupported FPNumber type " + thisType);
        }
    }

    /**
     * Generate a rounding operation for Float16, Float32, or Float64.
     */
    protected void generateBinaryRounding(CodeBuilder code, JitMethodDesc jmd, String mode) {
        ClassDesc valueCD = JitTypeDesc.getJavaPrimitive(thisType);
        assert valueCD != null;

        if (mode != null) {
            loadBinaryValueAsDouble(code, valueCD);
            code.invokestatic(CD_JavaMath, mode.equals("FLOOR") ? "floor" : "ceil",
                    MethodTypeDesc.of(CD_double, CD_double));
            if (valueCD.equals(CD_float)) {
                code.d2f();
            }
            addPrimitiveReturn(code, jmd);
            return;
        }

        Label finite = code.newLabel();
        load(code, valueCD, code.parameterSlot(0));
        code.invokestatic(valueCD.equals(CD_float) ? CD_JavaFloat : CD_JavaDouble,
                    "isFinite", MethodTypeDesc.of(CD_boolean, valueCD))
            .ifne(finite);
        load(code, valueCD, code.parameterSlot(0));
        addPrimitiveReturn(code, jmd);

        code.labelBinding(finite)
            .new_(CD_BigDecimal)
            .dup();
        loadBinaryValueAsDouble(code, valueCD);
        code.invokespecial(CD_BigDecimal, INIT_NAME, MethodTypeDesc.of(CD_void, CD_double));
        generateSetScale(code, jmd, null);
        code.invokevirtual(CD_BigDecimal, "doubleValue", MethodTypeDesc.of(CD_double));
        if (valueCD.equals(CD_float)) {
            code.d2f();
        }
        addPrimitiveReturn(code, jmd);
    }

    /**
     * Generate a rounding operation for Dec32, Dec64, or Dec128.
     */
    protected void generateDecimalRounding(CodeBuilder code, JitMethodDesc jmd, String mode) {
        ClassDesc   valueCD  = JitTypeDesc.getXvmPrimitiveClass(thisType);
        ClassDesc[] valueCDs = JitTypeDesc.getXvmPrimitiveClasses(thisType);
        assert valueCD != null;

        Label finite = code.newLabel();
        loadTarget(code, jmd);
        code.invokestatic(CD_DecimalFPNumber, "$leftmost7Bits",
                    MethodTypeDesc.of(CD_int, valueCDs))
            .invokestatic(CD_DecimalFPNumber, "$isFinite",
                    MethodTypeDesc.of(CD_boolean, CD_int))
            .ifne(finite);
        loadTarget(code, jmd);
        addPrimitiveReturn(code, jmd);

        code.labelBinding(finite)
            .aload(code.parameterSlot(jmd.optimizedCtx()));
        loadTarget(code, jmd);
        code.invokestatic(valueCD, "$toBigDecimal",
                MethodTypeDesc.of(CD_BigDecimal, valueCDs));
        generateSetScale(code, jmd, mode);

        ClassDesc returnCD = jmd.optimizedMD.returnType();
        code.invokestatic(valueCD, returnCD.equals(CD_int) ? "$toIntBits" : "$toLongBits",
                    MethodTypeDesc.of(returnCD, CD_Ctx, CD_BigDecimal));
        addReturn(code, returnCD);
    }

    /**
     * Load the primitive binary value as a double.
     */
    protected void loadBinaryValueAsDouble(CodeBuilder code, ClassDesc valueCD) {
        load(code, valueCD, code.parameterSlot(0));
        if (valueCD.equals(CD_float)) {
            code.f2d();
        } else if (!valueCD.equals(CD_double)) {
            throw new IllegalStateException("Unsupported binary FPNumber type " + thisType);
        }
    }

    /**
     * Load all primitive slots that represent the target value.
     */
    protected void loadTarget(CodeBuilder code, JitMethodDesc jmd) {
        for (int i = 0, count = jmd.optimizedParams.length;
                i < count && jmd.optimizedParams[i].index < 0; i++) {
            load(code, jmd.optimizedParams[i].cd, code.parameterSlot(i));
        }
    }

    /**
     * Round the BigDecimal on the stack to an integer scale.
     */
    protected void generateSetScale(CodeBuilder code, JitMethodDesc jmd, String mode) {
        code.iconst_0();
        if (mode == null) {
            Label specified = code.newLabel();
            Label loaded    = code.newLabel();
            int   modeSlot  = code.parameterSlot(
                    jmd.getImplicitParamCount() + jmd.getOptimizedParamIndex(0));

            code.aload(modeSlot)
                .dup()
                .ifnonnull(specified)
                .pop()
                .getstatic(CD_RoundingMode, "UP", CD_RoundingMode)
                .goto_(loaded)
                .labelBinding(specified)
                .invokevirtual(CD_Rounding, "$roundingMode",
                        MethodTypeDesc.of(CD_RoundingMode))
                .labelBinding(loaded);
        } else {
            code.getstatic(CD_RoundingMode, mode, CD_RoundingMode);
        }
        code.invokevirtual(CD_BigDecimal, "setScale",
                MethodTypeDesc.of(CD_BigDecimal, CD_int, CD_RoundingMode));
    }

    private static final ClassDesc CD_BigDecimal = ClassDesc.of("java.math.BigDecimal");
    private static final ClassDesc CD_RoundingMode = ClassDesc.of("java.math.RoundingMode");
    private static final ClassDesc CD_DecimalFPNumber =
            ClassDesc.of("org.xtclang.ecstasy.numbers.DecimalFPNumber");
    private static final ClassDesc CD_Rounding =
            ClassDesc.of("org.xtclang.ecstasy.numbers.FPNumber$Rounding");
}
