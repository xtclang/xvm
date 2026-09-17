package org.xvm.javajit.builders;

import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import java.lang.constant.ClassDesc;
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

    @Override
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
     * <p>
     * {@code return Int64.valueOf((rawBits & exponentMask) >>> significandBitLength);}
     */
    protected void generateExponentGet(CodeBuilder code, JitMethodDesc jmd) {
        generateGetExponent(code, jmd);
        code.areturn();
    }

    /**
     * Assemble an optimized static implementation of "significand$get$p()".
     * <p>
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
                        // IEEE-754-2008 spec Table 3.5 — Binary interchange format parameters for bit lengths >= 128
                        yield (int) Math.round((Math.log10(bitLength) / Math.log10(2)) * 4) - 13;
                    } else if (thisType.isA(pool().typeDecFPNumber()) && bitLength >= 32) {
                        // IEEE-754-2008 spec Table 3.6 — Decimal interchange format parameters for bit lengths >= 32
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
                    .invokestatic(CD_JavaFloat, "floatToRawIntBits", md(CD_int, CD_float))
                    .loadConstant(Integer.MAX_VALUE)
                    .iand()
                    .loadConstant(sigLen)
                    .iushr()
                    .i2l();
                break;
            case "D":
                code.dload(slot)
                    .invokestatic(CD_JavaDouble, "doubleToRawLongBits", md(CD_long, CD_double))
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
                    .invokestatic(CD_JavaFloat, "floatToRawIntBits", md(CD_int, CD_float))
                    .loadConstant(shift)
                    .ishl()
                    .loadConstant(shift)
                    .iushr()
                    .i2l();
                break;
            case "D":
                code.dload(slot)
                    .invokestatic(CD_JavaDouble, "doubleToRawLongBits", md(CD_long, CD_double))
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
     * <p>
     * {@code return finite ? bigDecimal.setScale(0, direction).toFPNumber() : this;}
     */
    protected void generateRound(CodeBuilder code, JitMethodDesc jmd) {
        generateRounding(code, jmd, null);
    }

    /**
     * Assemble an optimized static implementation of "floor$p()".
     * <p>
     * {@code return finite ? bigDecimal.setScale(0, FLOOR).toFPNumber() : this;}
     */
    protected void generateFloor(CodeBuilder code, JitMethodDesc jmd) {
        generateRounding(code, jmd, "FLOOR");
    }

    /**
     * Assemble an optimized static implementation of "ceil$p()".
     * <p>
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
            code.invokestatic(CD_JavaMath, mode.equals("FLOOR") ? "floor" : "ceil", md(CD_double, CD_double));
            narrowDoubleToCarrier(code, valueCD);
            addPrimitiveReturn(code, jmd);
            return;
        }

        Label finite = code.newLabel();
        load(code, valueCD, code.parameterSlot(0));
        loadIsFinite(code, valueCD);
        code.ifne(finite);
        load(code, valueCD, code.parameterSlot(0));
        addPrimitiveReturn(code, jmd);

        code.labelBinding(finite)
            .new_(CD_BigDecimal)
            .dup();
        loadBinaryValueAsDouble(code, valueCD);
        code.invokespecial(CD_BigDecimal, INIT_NAME, md(CD_void, CD_double));
        generateSetScale(code, jmd, null);
        code.invokevirtual(CD_BigDecimal, "doubleValue", md(CD_double));
        narrowDoubleToCarrier(code, valueCD);
        addPrimitiveReturn(code, jmd);
    }

    /**
     * Convert the double on the stack back to this type's primitive carrier.
     */
    protected CodeBuilder narrowDoubleToCarrier(CodeBuilder code, ClassDesc valueCD) {
        if (fp8ClassDesc() instanceof ClassDesc fp8CD && valueCD.equals(CD_int)) {
            // an FP8 value is carried as its 8-bit encoding, so re-encode the result
            return code.d2f().invokestatic(fp8CD, "$toBits", md(CD_int, CD_float));
        }
        return valueCD.equals(CD_float) ? code.d2f() : code;   // a double needs no narrowing
    }

    /**
     * Consume this type's primitive carrier from the stack and leave an "is finite" boolean.
     */
    protected CodeBuilder loadIsFinite(CodeBuilder code, ClassDesc valueCD) {
        if (fp8ClassDesc() instanceof ClassDesc fp8CD && valueCD.equals(CD_int)) {
            return code.invokestatic(fp8CD, "$finite", MD_FP8Predicate);
        }
        return code.invokestatic(valueCD.equals(CD_float) ? CD_JavaFloat : CD_JavaDouble,
                "isFinite", md(CD_boolean, valueCD));
    }

    /**
     * Generate a rounding operation for Dec32, Dec64, or Dec128.
     */
    protected void generateDecimalRounding(CodeBuilder code, JitMethodDesc jmd, String mode) {
        ClassDesc   valueCD  = JitTypeDesc.getXvmPrimitiveClass(thisType);
        ClassDesc[] valueCDs = JitTypeDesc.getXvmPrimitiveClasses(thisType);
        assert valueCD != null;

        Label finite = code.newLabel();
        loadTarget(code, jmd)
            .invokestatic(CD_DecimalFPNumber, "$leftmost7Bits", md(CD_int, valueCDs))
            .invokestatic(CD_DecimalFPNumber, "$isFinite", md(CD_boolean, CD_int))
            .ifne(finite);
        loadTarget(code, jmd);
        addPrimitiveReturn(code, jmd);

        code.labelBinding(finite)
            .aload(code.parameterSlot(jmd.optimizedCtx()));
        loadTarget(code, jmd)
            .invokestatic(valueCD, "$toBigDecimal", md(CD_BigDecimal, valueCDs));
        generateSetScale(code, jmd, mode);

        ClassDesc returnCD = jmd.optimizedMD.returnType();
        code.invokestatic(valueCD, returnCD.equals(CD_int) ? "$toIntBits" : "$toLongBits",
                    md(returnCD, CD_Ctx, CD_BigDecimal));
        addReturn(code, returnCD);
    }

    /**
     * Load the primitive binary value as a double.
     */
    protected void loadBinaryValueAsDouble(CodeBuilder code, ClassDesc valueCD) {
        CodeBuilder loaded = load(code, valueCD, code.parameterSlot(0));

        if (valueCD.equals(CD_double)) {
            return;                                     // nothing to widen
        }
        if (valueCD.equals(CD_float)) {
            loaded.f2d();
        } else if (fp8ClassDesc() instanceof ClassDesc fp8CD && valueCD.equals(CD_int)) {
            // an FP8 value is carried as its 8-bit encoding, so decode it before widening
            loaded.invokestatic(fp8CD, "$toFloat", md(CD_float, CD_int))
                  .f2d();
        } else {
            throw new IllegalStateException("Unsupported binary FPNumber type " + thisType);
        }
    }

    /**
     * @return the jitbridge ClassDesc for this type if it is one of the 8-bit FP formats, whose
     *         values are carried as their encoding rather than as a Java float; null otherwise
     */
    protected ClassDesc fp8ClassDesc() {
        return switch (thisType.getSingleUnderlyingClass(false).getName()) {
            case "Float8e4" -> CD_Float8e4;
            case "Float8e5" -> CD_Float8e5;
            default         -> null;
        };
    }

    /**
     * Load all primitive slots that represent the target value.
     */
    protected CodeBuilder loadTarget(CodeBuilder code, JitMethodDesc jmd) {
        for (int i = 0, count = jmd.optimizedParams.length;
                i < count && jmd.optimizedParams[i].index < 0; i++) {
            load(code, jmd.optimizedParams[i].cd, code.parameterSlot(i));
        }
        return code;
    }

    /**
     * Round the BigDecimal on the stack to an integer scale.
     */
    protected void generateSetScale(CodeBuilder code, JitMethodDesc jmd, String mode) {
        code.iconst_0();
        if (mode == null) {
            Label specified = code.newLabel();
            Label loaded    = code.newLabel();
            int   paramNo   = jmd.getImplicitParamCount() + jmd.getOptimizedParamIndex(0);
            int   modeSlot  = code.parameterSlot(paramNo);

            code.aload(modeSlot)
                .dup()
                .ifnonnull(specified)
                .pop()
                .getstatic(CD_RoundingMode, "UP", CD_RoundingMode)
                .goto_(loaded)
                .labelBinding(specified)
                .invokevirtual(CD_Rounding, "$roundingMode", md(CD_RoundingMode))
                .labelBinding(loaded);
        } else {
            code.getstatic(CD_RoundingMode, mode, CD_RoundingMode);
        }
        code.invokevirtual(CD_BigDecimal, "setScale", md(CD_BigDecimal, CD_int, CD_RoundingMode));
    }

    private static final ClassDesc CD_BigDecimal = ClassDesc.of("java.math.BigDecimal");
    private static final ClassDesc CD_RoundingMode = ClassDesc.of("java.math.RoundingMode");
    private static final ClassDesc CD_DecimalFPNumber =
            ClassDesc.of("org.xtclang.ecstasy.numbers.DecimalFPNumber");
    private static final ClassDesc CD_Rounding =
            ClassDesc.of("org.xtclang.ecstasy.numbers.FPNumber$Rounding");
}
