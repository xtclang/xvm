package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;
import java.lang.classfile.TypeKind;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.function.BiConsumer;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitTypeDesc;
import org.xvm.javajit.TypeSystem;

import static java.lang.constant.ConstantDescs.CD_Double;
import static java.lang.constant.ConstantDescs.CD_Float;
import static java.lang.constant.ConstantDescs.CD_Integer;
import static java.lang.constant.ConstantDescs.CD_Long;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_double;
import static java.lang.constant.ConstantDescs.CD_float;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;

/**
 * The builder for Number types.
 */
public class NumberBuilder extends AugmentingBuilder {

    /**
     * The properties that this builder has generated code for.
     */
    protected final Set<String> generatedProperties = new HashSet<>();

    protected static final MethodTypeDesc MD_floatToInt   = MethodTypeDesc.of(CD_int, CD_float);

    protected static final MethodTypeDesc MD_doubleToLong = MethodTypeDesc.of(CD_long, CD_double);

    public NumberBuilder(TypeSystem typeSystem, TypeSystem.Artifact art, ClassModel model) {
        super(typeSystem, art, model);
    }

    public static NumberBuilder builderFor(TypeSystem typeSystem, TypeSystem.Artifact art,
            ClassModel model) {

        ConstantPool pool = typeSystem.pool();
        TypeConstant type = art.type();

        if (type.isA(pool.typeFPNumber())) {
            return new FPNumberBuilder(typeSystem, art, model);
        }

        return new NumberBuilder(typeSystem, art, model);
    }

    @Override
    protected void assembleProperties(ClassBuilder classBuilder) {
        if (thisType.isJitPrimitive()) {
            // For JIT primitives, we generate code for static primitive property accessor
            // methods and optional wrapper instance methods that call them
            for (PropertyInfo prop : getProperties()) {
                if (prop.isFormalType()) {
                    continue;
                }
                String jitName = prop.getIdentity().ensureJitPropertyName(typeSystem);
                BiConsumer<CodeBuilder, JitMethodDesc> generator = getCodeGenerator(jitName);
                if (generator != null) {
                    generatedProperties.add(jitName);
                    assemblePrimitivePropertyAccessor(classBuilder, prop, generator);
                }
            }
            generateNumberFields(classBuilder);
        }
        super.assembleProperties(classBuilder);
    }

    @Override
    protected void assemblePropertyAccessor(ClassBuilder classBuilder, PropertyInfo prop,
                                            String jitName, JitMethodDesc jmd, boolean isGetter) {
        // do not generate code on JIT primitives for optimized property accessor methods
        // that this builder has already generated
        if (thisType.isJitPrimitive() && generatedProperties.contains(prop.getName())) {
            return;
        }
        super.assemblePropertyAccessor(classBuilder, prop, jitName, jmd, isGetter);
    }

    @Override
    protected void assembleMethods(ClassBuilder classBuilder) {
        super.assembleMethods(classBuilder);
    }

    protected Collection<PropertyInfo> getProperties() {
        return pool().typeNumber().ensureTypeInfo().getProperties().values();
    }

    protected BiConsumer<CodeBuilder, JitMethodDesc> getCodeGenerator(String jitName) {
        return switch (jitName) {
            case "bitLength"  -> this::generateBitLengthGet;
            case "bits"       -> this::generateBitsGet;
            case "byteLength" -> this::generateByteLengthGet;
            case "finite"     -> this::generateFiniteGet;
            case "infinity"   -> this::generateInfinityGet;
// TODO            case "magnitude"  -> this::generateMagnitudeGet;
            case "NaN"        -> this::generateNaNGet;
            case "negative"   -> this::generateNegativeGet;
            case "sign"       -> this::generateSignGet;
            case "signed"     -> this::generateSignedGet;
            default           -> null;
        };
    }

    protected void generateNumberFields(ClassBuilder classBuilder) {
        // no fields on plain Number
    }

    /**
     * Generate a static primitive property accessor for a property using the specified generator
     * function. Optionally generate an instance wrapper method that calls the static accessor.
     */
    protected void assemblePrimitivePropertyAccessor(ClassBuilder classBuilder, PropertyInfo prop,
            BiConsumer<CodeBuilder, JitMethodDesc> generator) {

        JitMethodDesc jmd   = prop.getGetterJitDesc(this, thisType);
        String        name  = prop.ensureGetterJitMethodName(typeSystem);
        int           flags = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC;

        // we are dealing with JIT primitives, so we should be generating a static primitive
        // property accessor method
        assert jmd.isOptimized && jmd.isOptimizedStatic;

        generatePrimitiveGetterWrapper(classBuilder, prop, name, jmd);

        if (isNativeMethod(name+OPT, jmd.optimizedMD)) {
            // the method has been manually coded so do not generate anything
            return;
        }
        classBuilder.withMethodBody(name+OPT, jmd.optimizedMD, flags,
                code -> generator.accept(code, jmd));
    }

    /**
     * Generate the primitive property getter wrapper method.
     * <p>
     * The generated method will be an instance method that calls the static primitive property
     * accessor method. The generated method would typically override the same instance method on
     * the superclass that defines the property.
     */
    protected void generatePrimitiveGetterWrapper(ClassBuilder classBuilder, PropertyInfo prop,
            String methodName, JitMethodDesc jmd) {

        TypeConstant   parentType = prop.getIdentity().getParentConstant().getType();
        JitMethodDesc  jmdWrapper = prop.getGetterJitDesc(this, parentType);
        String         jitName    = jmdWrapper.isOptimized ? methodName + OPT : methodName;
        MethodTypeDesc md         = jmdWrapper.isOptimized
                                        ? jmdWrapper.optimizedMD
                                        : jmdWrapper.standardMD;

        if (isNativeMethod(jitName, md)) {
            // the method has been manually coded
            return;
        }

        ClassDesc CD_this = art.CD();
        ClassDesc cdRet   = md.returnType();
        int       flags   = ClassFile.ACC_PUBLIC;

        classBuilder.withMethodBody(jitName, md, flags, code -> {
            code.aload(0);
            unbox(code, thisType);
            loadCtx(code);
            code.invokestatic(CD_this, methodName + OPT, jmd.optimizedMD);
            addReturn(code, cdRet);
        });
    }

    /**
     * Assemble the static primitive accessor "bits$get$p" method, for example:
     * <pre>
     *     ArrayᐸBitᐳ bits$get$p(T thi$, Ctx ctx)
     * </pre>
     */
    protected void generateBitsGet(CodeBuilder code, JitMethodDesc jmd) {
        long bitLength = getBitLength();

        // we will create an instance of ArrayᐸBitᐳ by calling the static helper method
        // $fromLongs the signature is ArrayᐸBitᐳ $fromLongs(Ctx ctx, long bitLength, long[] bits)

        // load the Ctx and bitLength parameters
        code.aload(code.parameterSlot(jmd.optimizedCtx()))
            .loadConstant(bitLength);

        // create an array of longs to hold each of the method parameters passed to
        // the bits$get$p method except the last Ctx param
        ClassDesc[] params = jmd.optimizedMD.parameterArray();
        int         size   = params.length - 1;
        code.loadConstant(size)
            .newarray(TypeKind.LONG);

        // the long[] is duplicated in the top stack slot
        // populate the array with the params converted to longs
        for (int i = 0; i < size; i++) {
            // duplicate the array on the stack and load the index to the stack
            code.dup()
                .loadConstant(i);
            // loat the parameter, converting to a primitive long
            int slot = code.parameterSlot(i);
            switch (params[i].descriptorString()) {
            case "I", "S", "B", "Z":
                code.iload(slot)
                    .i2l();
                break;
            case "J":
                code.lload(slot);
                break;
            case "F":
                code.fload(slot)
                    .invokestatic(CD_JavaFloat, "floatToRawIntBits", MD_floatToInt)
                    .i2l();
                break;
            case "D":
                code.dload(slot)
                    .invokestatic(CD_JavaDouble, "doubleToRawLongBits", MD_doubleToLong);
                break;
            default:
                throw new IllegalStateException();
            }
            // store the long into the array
            code.lastore();
        }

        // the populated long array is on the top of the stack
        // create the ArrayᐸBitᐳ and return it
        MethodTypeDesc md = MethodTypeDesc.of(CD_ArrayBit, CD_Ctx, CD_long, CD_long.arrayType());
        code.invokestatic(CD_ArrayBit, "$fromLongs", md)
            .areturn();
    }

    /**
     * Assemble the static primitive accessor "bitLength$get$p" method, for example:
     * <pre>
     *     long bitLength$get$p(int thi$, Ctx ctx)
     * </pre>
     */
    protected void generateBitLengthGet(CodeBuilder code, JitMethodDesc jmd) {
        long bitLength = getBitLength();
        code.loadConstant(bitLength)
            .lreturn();
    }

    /**
     * Assemble the static primitive accessor "byteLength$get$p" method, for example:
     * <pre>
     *     long byteLength$get$p(int thi$, Ctx ctx)
     * </pre>
     */
    protected void generateByteLengthGet(CodeBuilder code, JitMethodDesc jmd) {
        long bitLength = getBitLength();
        code.loadConstant((bitLength + 7) / 8L)
            .lreturn();
    }

    /**
     * Assemble the static primitive accessor "signed$get$p" method, for example:
     * <pre>
     *     boolean signed$get$p(int thi$, Ctx ctx)
     * </pre>
     */
    protected void generateSignedGet(CodeBuilder code, JitMethodDesc jmd) {
        boolean signed = !thisType.isA(pool().typeUIntNumber());
        code.loadConstant(signed ? 1 : 0)
            .ireturn();
    }

    /**
     * Assemble the static primitive accessor "sign$get$p" method, for example:
     * <pre>
     *     Number.Signum sign$get$p(int thi$, Ctx ctx)
     * </pre>
     */
    protected void generateSignGet(CodeBuilder code, JitMethodDesc jmd) {
        ConstantPool pool         = pool();
        TypeConstant typeSignum   = pool.ensureVirtualChildTypeConstant(pool.typeNumber(), "Signum");
        TypeConstant typePositive = pool.ensureVirtualChildTypeConstant(typeSignum, "Positive");
        TypeConstant typeNegative = pool.ensureVirtualChildTypeConstant(typeSignum, "Negative");
        TypeConstant typeZero     = pool.ensureVirtualChildTypeConstant(typeSignum, "Zero");
        ClassDesc    cdPositive   = JitTypeDesc.getJitClass(this, typePositive);
        ClassDesc    cdNegative   = JitTypeDesc.getJitClass(this, typeNegative);
        ClassDesc    cdZero       = JitTypeDesc.getJitClass(this, typeZero);

        boolean signed        = !thisType.isA(pool().typeUIntNumber());
        Label   labelZero     = code.newLabel();
        Label   labelPositive = code.newLabel();

        // generate ops to compare to zero, leaving the result is on the stack
        generateSignum(code, jmd);

        code.dup()
            .iconst_0()
            .if_icmpeq(labelZero); // the value is zero, jump to labelZero

        if (signed) {
            // type is signed so check for less than zero, else must be positive
            code.iconst_0()
                .if_icmpgt(labelPositive)
                .getstatic(cdNegative, Instance, cdNegative)
                .areturn();
        } else {
            // unsigned so must be positive, pop the dup of the compare result
            code.pop();
        }

        code.labelBinding(labelPositive)
            .getstatic(cdPositive, Instance, cdPositive)
            .areturn()
            .labelBinding(labelZero)
            .pop()
            .getstatic(cdZero, Instance, cdZero)
            .areturn();
    }

    /**
     * Assemble the static primitive accessor "negative$get$p" method, for example:
     * <pre>
     *     boolean negative$get$p(int thi$, Ctx ctx)
     * </pre>
     */
    protected void generateNegativeGet(CodeBuilder code, JitMethodDesc jmd) {
        boolean unsigned = thisType.isA(pool().typeUIntNumber());
        Label   labelNeg = code.newLabel();

        if (unsigned) {
            // unsigned, cannot be negative
            code.iconst_0()
                .ireturn();
        }

        // generate ops to compare to zero, leaving the result is on the stack
        generateSignum(code, jmd);

        code.iconst_0()
            .if_icmplt(labelNeg)
            .iconst_0()
            .ireturn()
            .labelBinding(labelNeg)
            .iconst_1()
            .ireturn();
    }

    /**
     * Assemble the static primitive accessor "finite$get$p" method, for example:
     * <pre>
     *     boolean finite$get$p(int thi$, Ctx ctx)
     * </pre>
     */
    protected void generateFiniteGet(CodeBuilder code, JitMethodDesc jmd) {
        String name      = thisType.getSingleUnderlyingClass(false).getName();
        int    paramSlot = code.parameterSlot(0);

        if (thisType.isA(pool().typeBinFPNumber())) {
            switch (name) {
                case "Float16", "Float32":
                    code.fload(paramSlot)
                            .invokestatic(CD_Float, "isFinite", MethodTypeDesc.of(CD_boolean, CD_float))
                            .ireturn();
                    break;

                case "Float64":
                    code.dload(paramSlot)
                            .invokestatic(CD_Double, "isFinite", MethodTypeDesc.of(CD_boolean, CD_double))
                            .ireturn();
                    break;

                default:
                    throw new UnsupportedOperationException("Unsupported Binary FP type: " + name);
            }
        } else {
            // must be finite
            code.iconst_1()
                .ireturn();
        }
    }

    /**
     * Assemble the static primitive accessor "infinity$get$p" method, for example:
     * <pre>
     *     boolean infinity$get$p(int thi$, Ctx ctx)
     * </pre>
     */
    protected void generateInfinityGet(CodeBuilder code, JitMethodDesc jmd) {
        if (thisType.isA(pool().typeBinFPNumber())) {
            String name      = thisType.getSingleUnderlyingClass(false).getName();
            int    paramSlot = code.parameterSlot(0);

            switch (name) {
                case "Float16", "Float32":
                    code.fload(paramSlot)
                        .invokestatic(CD_Float, "isInfinite", MethodTypeDesc.of(CD_boolean, CD_float))
                        .ireturn();
                    break;

                case "Float64":
                    code.dload(paramSlot)
                        .invokestatic(CD_Double, "isInfinite", MethodTypeDesc.of(CD_boolean, CD_double))
                        .ireturn();
                    break;

                default:
                    throw new UnsupportedOperationException("Unsupported Binary FP type: " + name);
            }
        } else {
            // cannot be infinity
            code.iconst_0()
                .ireturn();
        }
    }

    /**
     * Assemble the static primitive accessor "NaN$get$p" method, for example:
     * <pre>
     *     boolean NaN$get$p(float thi$, Ctx ctx)
     * </pre>
     */
    protected void generateNaNGet(CodeBuilder code, JitMethodDesc jmd) {
        if (thisType.isA(pool().typeBinFPNumber())) {
            String name      = thisType.getSingleUnderlyingClass(false).getName();
            int    paramSlot = code.parameterSlot(0);

            switch (name) {
                case "Float16", "Float32":
                    code.fload(paramSlot)
                        .invokestatic(CD_Float, "isNaN", MethodTypeDesc.of(CD_boolean, CD_float))
                        .ireturn();
                    break;

                case "Float64":
                    code.dload(paramSlot)
                        .invokestatic(CD_Double, "isNaN", MethodTypeDesc.of(CD_boolean, CD_double))
                        .ireturn();
                    break;

                default:
                    throw new UnsupportedOperationException("Unsupported Binary FP type: " + name);
            }
        } else {
            // cannot be infinity
            code.iconst_0()
                .ireturn();
        }
    }

    /**
     * Assemble the static primitive accessor "NaN$get$p" method, for example:
     * <pre>
     *     boolean NaN$get$p(int thi$, Ctx ctx)
     * </pre>
     */
    // TODO this is currently incorrect but issues with things like capped methods for properties
    // mean that it cannot be tested and debugged
    protected void generateMagnitudeGet(CodeBuilder code, JitMethodDesc jmd) {
        String    name      = thisType.getSingleUnderlyingClass(false).getName();
        int       paramSlot = code.parameterSlot(0);
        ClassDesc cdMath    = ClassDesc.of(Math.class.getName());

        switch (name) {
        case "Int8", "Int16", "Int32":
            code.lload(paramSlot)
                .invokestatic(cdMath, "abs", MethodTypeDesc.of(CD_int, CD_int));
            break;

        case "Bit", "Nibble", "UInt8", "UInt16", "UInt32":
            code.iload(paramSlot)
                .ireturn();
            break;

        case "Int64":
            code.lload(paramSlot)
                .invokestatic(cdMath, "abs", MethodTypeDesc.of(CD_long, CD_long));
            break;

        case "UInt64":
            code.lload(paramSlot)
                .lreturn();
            break;

        case "Float16", "Float32":
            code.lload(paramSlot)
                .invokestatic(cdMath, "abs", MethodTypeDesc.of(CD_float, CD_float));
            break;

        case "Float64":
            code.lload(paramSlot)
                .invokestatic(cdMath, "abs", MethodTypeDesc.of(CD_double, CD_double));
            break;

        case "Dec32":
            code.iload(paramSlot);
            loadCtx(code);
            code.invokestatic(art.CD(), "abs$p",
                    MethodTypeDesc.of(CD_int, CD_int, CD_Ctx));
            break;

        case "Dec64":
            code.lload(paramSlot);
            loadCtx(code);
            code.invokestatic(art.CD(), "abs$p",
                    MethodTypeDesc.of(CD_long, CD_long, CD_Ctx));
            break;

        case "Dec128", "Int128":
            code.lload(code.parameterSlot(0));
            code.lload(code.parameterSlot(1));
            loadCtx(code);
            code.invokestatic(art.CD(), "abs$p",
                    MethodTypeDesc.of(CD_long, CD_long, CD_long, CD_Ctx));
            break;

        case "UInt128":
            code.lload(code.parameterSlot(1));
            storeToContext(code, CD_long, 0);
            code.lload(code.parameterSlot(0))
                .lreturn();
            break;

        default:
            throw new UnsupportedOperationException("Unsupported number type " + name);
        }
    }

    /**
     * Generate the byte codes to compare the primitive value(s) for this type to zero,
     * leaving the resulting int on the stack. The result is the normal Java compare result,
     * 0 if the value is zero, -1 if less than zero, and 1 if greater than zero.
     */
    protected void generateSignum(CodeBuilder code, JitMethodDesc jmd) {
        ConstantPool pool = pool();

        if (thisType.isA(pool.typeFPNumber())) {
            // for IEEE 754-2008 zero can be positive or negative, so all we need to do is check
            // the sign bit
            // we call a helper method on FPNumber to obtain the signum for this value
            // the helper signature params are the same as the method we are generating code for
            ClassDesc      cd     = JitTypeDesc.getJitClass(this, pool.typeFPNumber());
            MethodTypeDesc md     = MethodTypeDesc.of(CD_int, jmd.optimizedMD.parameterArray());
            ClassDesc[]    params = md.parameterArray();
            for (int i = 0; i < params.length; i++) {
                load(code, params[i], code.parameterSlot(i));
            }
            code.invokestatic(cd, "$signum", md);
        } else {
            String name      = thisType.getSingleUnderlyingClass(false).getName();
            int    paramSlot = code.parameterSlot(0);

            switch (name) {
            case "Bit", "Nibble", "Int8", "Int16", "Int32":
                // compare the int in the first parameter slot to zero
                // we use lcmp() as there is no int compare op
                code.iload(paramSlot)
                    .i2l()
                    .lconst_0()
                    .lcmp();
                break;

            case "UInt8", "UInt16", "UInt32":
                // compare unsigned int in first parameter slot to zero
                // using Integer.compareUnsigned
                code.iload(paramSlot)
                    .iconst_0()
                    .invokestatic(CD_Integer, "compareUnsigned",
                            MethodTypeDesc.of(CD_int, CD_int, CD_int));
                break;

            case "Int64":
                // compare the long in the first parameter slot to zero
                code.lload(paramSlot)
                    .lconst_0()
                    .lcmp();
                break;

            case "UInt64":
                // compare unsigned long in first parameter slot to zero
                // using Long.compareUnsigned
                code.lload(paramSlot)
                    .lconst_0()
                    .invokestatic(CD_Long, "compareUnsigned",
                            MethodTypeDesc.of(CD_int, CD_long, CD_long));
                break;

            case "Int128", "UInt128":
                // call the static compare helper method to compare to zero
                code.lload(code.parameterSlot(0))
                    .lload(code.parameterSlot(1))
                    .loadConstant(0L)
                    .loadConstant(0L)
                    .invokestatic(art.CD(), "$compare",
                            MethodTypeDesc.of(CD_int, CD_long, CD_long, CD_long, CD_long));
                break;

            default:
                throw new UnsupportedOperationException("Unsupported number type " + name);
            }
        }

    }

    /**
     * @return the bit length of this type
     */
    protected int getBitLength() {
        String name = thisType.getSingleUnderlyingClass(false).getName();

        return switch (name) {
            case "Bit"                                    -> 1;
            case "Nibble"                                 -> 4;
            case "Int8", "UInt8", "Float8e4", "Float8e5"  -> 8;
            case "BFloat16", "Float16", "Int16", "UInt16" -> 16;
            case "Dec32", "Float32", "Int32", "UInt32"    -> 32;
            case "Dec64", "Float64", "Int64", "UInt64"    -> 64;
            case "Dec128", "Int128", "UInt128"            -> 128;
            default -> throw new UnsupportedOperationException("Unsupported number type " + name);
        };
    }
}
