package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;

import java.util.Collection;

import java.util.function.BiConsumer;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitTypeDesc;
import org.xvm.javajit.TypeSystem;

import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;

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
    protected BiConsumer<CodeBuilder, JitMethodDesc> getCodeGenerator(String jitName) {
        return switch (jitName) {
            case "exponent"             -> this::generateExponentGet;
            case "exponentBitLength"    -> this::generateExponentBitLengthGet;
            case "precision"            -> this::generatePrecisionGet;
            case "radix"                -> this::generateRadixGet;
            case "significand"          -> this::generateSignificandGet;
            case "significandBitLength" -> this::generateSignificandBitLengthGet;
            default -> super.getCodeGenerator(jitName);
        };
    }

    @Override
    // TODO - this can probably be removed after the fix to generate the static initializer for
    //  properties
    protected void generateNumberFields(ClassBuilder classBuilder) {
        int flags = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC;
        if (thisType.isJavaPrimitive()) {
            ClassDesc cd = JitTypeDesc.getJavaPrimitive(thisType);
            classBuilder.withField("PositiveInfinity", cd, flags);
            classBuilder.withField("NegativeInfinity", cd, flags);
            classBuilder.withField("PositiveNaN", cd, flags);
            classBuilder.withField("NegativeNaN", cd, flags);
        } else {
            // must be XVM primitive
            ClassDesc[] cds = JitTypeDesc.getXvmPrimitiveClasses(thisType);
            for (int i = 0; i < cds.length; i++) {
                ClassDesc cd = cds[i];
                classBuilder.withField("PositiveInfinity$" + i, cd, flags);
                classBuilder.withField("NegativeInfinity$" + i, cd, flags);
                classBuilder.withField("PositiveNaN$" + i, cd, flags);
                classBuilder.withField("NegativeNaN$" + i, cd, flags);
            }
        }
    }

    // TODO - this can probably be removed after the fix to generate the static initializer for
    //  properties
    @Override
    protected void augmentCLInit(CodeBuilder code) {
        augmentFPNumberCLInit(code);
        // must call super last as AugmentingBuilder can inject a "return" op
        super.augmentCLInit(code);
    }

    /**
     * Assemble the static primitive accessor "radix$get$p" method, for example:
     * <pre>
     *     long radix$get$p(int thi$, Ctx ctx)
     * </pre>
     */
    protected void generateExponentBitLengthGet(CodeBuilder code, JitMethodDesc jmd) {
        long exp = getExponentLength();
        code.loadConstant(exp)
            .lreturn();
    }

    /**
     * Assemble the static primitive accessor "precision$get$p" method, for example:
     * <pre>
     *     long precision$get$p(int thi$, Ctx ctx)
     * </pre>
     */
    protected void generatePrecisionGet(CodeBuilder code, JitMethodDesc jmd) {
        long p = getSignificandLength() + 1;
        code.loadConstant(p)
            .lreturn();
    }

    /**
     * Assemble the static primitive accessor "significandBitLength$get$p" method, for example:
     * <pre>
     *     long significandBitLength$get$p(int thi$, Ctx ctx)
     * </pre>
     */
    protected void generateSignificandBitLengthGet(CodeBuilder code, JitMethodDesc jmd) {
        long p = getSignificandLength();
        code.loadConstant(p)
            .lreturn();
    }

    /**
     * Assemble the static primitive accessor "exponent$get$p" method, for example:
     * <pre>
     *     long exponent$get$p(int thi$, Ctx ctx)
     * </pre>
     */
    protected void generateExponentGet(CodeBuilder code, JitMethodDesc jmd) {
        generateGetExponent(code, jmd);
        code.areturn();
    }

    /**
     * Assemble the static primitive accessor "significand$get$p" method, for example:
     * <pre>
     *     long significand$get$p(int thi$, Ctx ctx)
     * </pre>
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
        String name = thisType.getSingleUnderlyingClass(false).getName();

        return switch (name) {
            case "Float8e4"                     -> 4;
            case "Float8e5", "Float16"          -> 5;
            case "Dec32"                        -> 6;
            case "BFloat16", "Float32", "Dec64" -> 8;
            case "Float64"                      -> 11;
            case "Dec128"                       -> 12;
            default -> throw new UnsupportedOperationException("Unsupported number type " + name);
        };
    }

    /**
     * Assemble the static primitive accessor "radix$get$p" method, for example:
     * <pre>
     *     long radix$get$p(int thi$, Ctx ctx)
     * </pre>
     */
    protected void generateRadixGet(CodeBuilder code, JitMethodDesc jmd) {
        long radix = thisType.isA(pool().typeBinFPNumber()) ? 2L : 10L;
        code.loadConstant(radix)
            .lreturn();
    }

    /**
     * Add code to the clinit method that will initialize the various FPNumber static fields.
     */
    // TODO - this can probably be removed after the fix to generate the static initializer for
    //  properties
    protected void augmentFPNumberCLInit(CodeBuilder code) {
        TypeConstant superType = pool().typeFPNumber();
        if (!thisType.isA(superType)) {
            // this type is not a FPNumber
            return;
        }

        if (thisType.isJavaPrimitive()) {
            ClassDesc cd  = JitTypeDesc.getJavaPrimitive(thisType);
            assert cd != null;

            long inf;
            long negInf;

            switch (cd.descriptorString()) {
                case "I", "S", "B", "Z":
                    negInf = calculateNegativeInfinity(32);
                    inf    = negInf & Long.MAX_VALUE;
                    code.loadConstant((int) (inf >>> 32))
                            .putstatic(art.CD(), "PositiveInfinity", cd)
                            .loadConstant((int) (negInf >>> 32))
                            .putstatic(art.CD(), "NegativeInfinity", cd)
                            .loadConstant(Integer.MAX_VALUE)
                            .putstatic(art.CD(), "PositiveNaN", cd)
                            .loadConstant(Integer.MIN_VALUE)
                            .putstatic(art.CD(), "NegativeNaN", cd);
                    break;

                case "J":
                    negInf = calculateNegativeInfinity(64);
                    inf    = negInf & Long.MAX_VALUE;
                    code.loadConstant(inf)
                            .putstatic(art.CD(), "PositiveInfinity", cd)
                            .loadConstant(negInf)
                            .putstatic(art.CD(), "NegativeInfinity", cd)
                            .loadConstant(Long.MAX_VALUE)
                            .putstatic(art.CD(), "PositiveNaN", cd)
                            .loadConstant(Long.MIN_VALUE)
                            .putstatic(art.CD(), "NegativeNaN", cd);
                    break;

                case "F":
                    int intNan = Float.floatToRawIntBits(Float.NaN) | 0x80000000;
                    code.loadConstant(Float.POSITIVE_INFINITY)
                            .putstatic(art.CD(), "PositiveInfinity", cd)
                            .loadConstant(Float.NEGATIVE_INFINITY)
                            .putstatic(art.CD(), "NegativeInfinity", cd)
                            .loadConstant(Float.NaN)
                            .putstatic(art.CD(), "PositiveNaN", cd)
                            .loadConstant(Float.intBitsToFloat(intNan))
                            .putstatic(art.CD(), "NegativeNaN", cd);
                    break;

                case "D":
                    long longNan = Double.doubleToRawLongBits(Double.NaN) | 0x8000000000000000L;
                    code.loadConstant(Double.POSITIVE_INFINITY)
                            .putstatic(art.CD(), "PositiveInfinity", cd)
                            .loadConstant(Double.NEGATIVE_INFINITY)
                            .putstatic(art.CD(), "NegativeInfinity", cd)
                            .loadConstant(Double.NaN)
                            .putstatic(art.CD(), "PositiveNaN", cd)
                            .loadConstant(Double.longBitsToDouble(longNan))
                            .putstatic(art.CD(), "NegativeNaN", cd);
                    break;

                default:
                    throw new UnsupportedOperationException("Unsupported number type " + cd);
            }
        } else if (thisType.isXvmPrimitive()) {
            String name = thisType.getSingleUnderlyingClass(false).getName();

            switch (name) {
                case "Dec32":
                    code.loadConstant(0b011110 << 26)
                            .putstatic(art.CD(), "PositiveInfinity$0", CD_int)
                            .loadConstant(0b111110 << 26)
                            .putstatic(art.CD(), "NegativeInfinity$0", CD_int)
                            .loadConstant(0b011111 << 26)
                            .putstatic(art.CD(), "PositiveNaN$0", CD_int)
                            .loadConstant(0b111111 << 26)
                            .putstatic(art.CD(), "NegativeNaN$0", CD_int);
                    break;

                case "Dec64":
                    code.loadConstant(0b011110L << 58)
                            .putstatic(art.CD(), "PositiveInfinity$0", CD_long)
                            .loadConstant(0b111110L << 58)
                            .putstatic(art.CD(), "NegativeInfinity$0", CD_long)
                            .loadConstant(0b011111L << 58)
                            .putstatic(art.CD(), "PositiveNaN$0", CD_long)
                            .loadConstant(0b111111L << 58)
                            .putstatic(art.CD(), "NegativeNaN$0", CD_long);
                    break;

                case "Dec128":
                    code.loadConstant(0b011110L << 58)
                            .putstatic(art.CD(), "PositiveInfinity$1", CD_long)
                            .loadConstant(0b111110L << 58)
                            .putstatic(art.CD(), "NegativeInfinity$1", CD_long)
                            .loadConstant(0b011111L << 58)
                            .putstatic(art.CD(), "PositiveNaN$1", CD_long)
                            .loadConstant(0b111111L << 58)
                            .putstatic(art.CD(), "NegativeNaN$1", CD_long);
                    break;

                default:
                    throw new UnsupportedOperationException("Unsupported number type " + name);
            }
        }
    }

    protected long calculateNegativeInfinity(int bitLength) {
        ConstantPool pool = pool();
        if (thisType.isA(pool.typeBinFPNumber())) {
            String name = thisType.getSingleUnderlyingClass(false).getName();
            int exp = switch (name) {
                case "BFloat16" -> 8;
                case "Float8e4" -> 4;
                case "Float8e5" -> 5;
                default -> switch (bitLength) {
                    case 16  -> 5;
                    case 32  -> 8;
                    case 64  -> 11;
                    case 128 -> 15;
                    case 256 -> 19;
                    default -> throw new UnsupportedOperationException("Unsupported bitLength "
                            + bitLength);
                };
            };
            return -1L << (bitLength - exp - 1) << (64 - bitLength);
        }

        if (thisType.isA(pool.typeDecFPNumber())) {
            return 0L;
        }

        throw new UnsupportedOperationException("Unsupported number type " + thisType);
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
                    .invokestatic(CD_JavaFloat, "floatToRawIntBits", MD_floatToInt)
                    .loadConstant(Integer.MAX_VALUE)
                    .iand()
                    .loadConstant(sigLen)
                    .iushr()
                    .i2l();
                break;
            case "D":
                code.dload(slot)
                    .invokestatic(CD_JavaDouble, "doubleToRawLongBits", MD_doubleToLong)
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
                    .invokestatic(CD_JavaFloat, "floatToRawIntBits", MD_floatToInt)
                    .loadConstant(shift)
                    .ishl()
                    .loadConstant(shift)
                    .iushr()
                    .i2l();
                break;
            case "D":
                code.dload(slot)
                    .invokestatic(CD_JavaDouble, "doubleToRawLongBits", MD_doubleToLong)
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
}
