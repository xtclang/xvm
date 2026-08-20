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

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
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
import static java.lang.constant.ConstantDescs.CD_short;

/**
 * The builder for Number types.
 */
public class NumberBuilder extends AugmentingBuilder {

    /**
     * The properties that this builder has generated code for.
     */
    protected final Set<String> generatedProperties = new HashSet<>();

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

        if (type.isA(pool.typeIntNumber())) {
            return new IntNumberBuilder(typeSystem, art, model);
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
                PropertyInfo declaredProp = prop;
                prop = typeInfo.findProperty(prop.getName());
                assert prop != null;

                String jitName = prop.getIdentity().ensureJitPropertyName(typeSystem);
                BiConsumer<CodeBuilder, JitMethodDesc> generator = getPropertyCodeGenerator(jitName);
                if (generator != null && generatedProperties.add(jitName)) {
                    assemblePrimitivePropertyAccessor(classBuilder, prop, declaredProp, generator);
                }
            }
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

    protected Collection<PropertyInfo> getProperties() {
        return pool().typeNumber().ensureTypeInfo().getProperties().values();
    }

    protected BiConsumer<CodeBuilder, JitMethodDesc> getPropertyCodeGenerator(String jitName) {
        return switch (jitName) {
            case "bitLength"  -> this::generateBitLengthGet;
            case "bits"       -> this::generateBitsGet;
            case "byteLength" -> this::generateByteLengthGet;
            case "finite"     -> this::generateFiniteGet;
            case "infinity"   -> this::generateInfinityGet;
            case "magnitude"  -> this::generateMagnitudeGet;
            case "NaN"        -> this::generateNaNGet;
            case "negative"   -> this::generateNegativeGet;
            case "sign"       -> this::generateSignGet;
            case "signed"     -> this::generateSignedGet;
            default           -> null;
        };
    }

    protected BiConsumer<CodeBuilder, JitMethodDesc> getMethodCodeGenerator(String jitName) {
        return switch (jitName) {
            case "toBitArray", "toNibbleArray", "toByteArray" ->
                    (code, jmd) -> generateToArray(code, jmd, jitName);
            default -> null;
        };
    }

    protected boolean shouldCompileNaturally(MethodInfo method) {
        String name = method.getJitIdentity().getName();
        return name.equals("zero") || name.equals("one") ||
                name.equals("fixedBitLength") || name.equals("range");
    }

    @Override
    protected boolean shouldGenerate(IdentityConstant id) {
        if (thisType.isJitPrimitive() && id instanceof MethodConstant &&
                !(id.getNamespace() instanceof PropertyConstant)) {
            // property accessors are handled by assembleProperties()
            return true;
        }
        return super.shouldGenerate(id);
    }

    @Override
    protected void assembleMethod(
            ClassBuilder classBuilder, MethodInfo method,
            String jitName, JitMethodDesc jmd) {
        if (!thisType.isJitPrimitive()) {
            super.assembleMethod(classBuilder, method, jitName, jmd);
            return;
        }

        if (method.getHead().getMethodStructure().isPropertyInitializer()) {
            super.assembleMethod(classBuilder, method, jitName, jmd);
            return;
        }

        String         methodName = jmd.isOptimized ? jitName+OPT : jitName;
        MethodTypeDesc md         = jmd.isOptimized ? jmd.optimizedMD : jmd.standardMD;
        if (isNativeMethod(methodName, md)) {
            super.assembleMethod(classBuilder, method, jitName, jmd);
            return;
        }

        if (shouldCompileNaturally(method)) {
            super.assembleMethod(classBuilder, method, jitName, jmd);
            return;
        }

        BiConsumer<CodeBuilder, JitMethodDesc> generator = jmd.isPrimitivized()
                ? getMethodCodeGenerator(method.getJitIdentity().getName())
                : null;
        if (generator == null) {
            generator = this::generateUnsupported;
        }
        assembleGeneratedMethod(classBuilder, method, jitName, jmd, generator);
    }

    /**
     * Generate a method for a JIT primitive and any routing methods required to call it.
     */
    protected void assembleGeneratedMethod(ClassBuilder classBuilder, MethodInfo method,
            String jitName, JitMethodDesc jmd,
            BiConsumer<CodeBuilder, JitMethodDesc> generator) {
        boolean        isOptimized = jmd.isOptimized;
        String         methodName  = isOptimized ? jitName+OPT : jitName;
        MethodTypeDesc md          = isOptimized ? jmd.optimizedMD : jmd.standardMD;
        boolean        isStatic    = isOptimized ? jmd.isOptimizedStatic : jmd.isStandardStatic;
        int            flags       = ClassFile.ACC_PUBLIC |
                (isStatic ? ClassFile.ACC_STATIC : 0);

        classBuilder.withMethodBody(methodName, md, flags,
                code -> generator.accept(code, jmd));

        if (isOptimized && !isNativeMethod(jitName, jmd.standardMD)) {
            assembleMethodWrapper(classBuilder, jitName, jmd);
        }

        if (jmd.isPrimitivized()) {
            TypeConstant  typeDeclared = method.getJitIdentity().getClassIdentity().getType();
            JitMethodDesc jmdDeclared  = method.getJitDesc(this, typeDeclared);
            if (jmdDeclared.isOptimized && !jmdDeclared.isOptimizedStatic &&
                    !isNativeMethod(jitName+OPT, jmdDeclared.optimizedMD)) {
                assembleOptimizedCap(classBuilder, jitName+OPT, jitName+OPT, jmdDeclared, jmd);
            }
        }
    }

    /**
     * Generate a placeholder method that throws Unsupported.
     */
    protected void generateUnsupported(CodeBuilder code, JitMethodDesc jmd) {
        code.aload(code.parameterSlot(jmd.optimizedCtx()))
            .aconst_null()
            .invokestatic(CD_Exception, "$unsupported",
                    MethodTypeDesc.of(CD_nException, CD_Ctx, CD_JavaString))
            .athrow();
    }

    /**
     * Return the primitive value currently on the stack.
     */
    protected void addPrimitiveReturn(CodeBuilder code, JitMethodDesc jmd) {
        int returnCount = jmd.optimizedReturns.length;
        int ctxSlot     = code.parameterSlot(jmd.optimizedCtx());
        for (int i = returnCount - 1; i > 0; i--) {
            storeToContext(code, jmd.optimizedReturns[i].cd,
                    jmd.optimizedReturns[i].altIndex, ctxSlot);
        }
        addReturn(code, jmd.optimizedMD.returnType());
    }

    @Override
    protected void assembleNew(ClassBuilder classBuilder, MethodInfo constructor,
                               String jitName, JitMethodDesc jmd) {
        if (!thisType.isJitPrimitive()) {
            super.assembleNew(classBuilder, constructor, jitName, jmd);
            return;
        }

        TypeConstant[] paramTypes = constructor.getSignature().getRawParams();
        if (paramTypes.length != 1) {
            // all other constructors require native new functions
            return;
        }

        ConstantPool pool        = pool();
        TypeConstant paramType   = paramTypes[0];
        boolean      isBitArray  = paramType.isA(pool.typeBitArray());
        boolean      isByteArray = paramType.isA(pool.typeByteArray());

        if (!isBitArray && !isByteArray) {
            // all other constructors require native new functions
            return;
        }

        assert !jmd.isOptimized && jmd.standardParams.length == 1;

        int       bitLength    = getBitLength();
        long      expectedSize = isBitArray ? bitLength : (bitLength + 7L) >>> 3;
        ClassDesc arrayCD      = jmd.standardParams[0].cd;

        classBuilder.withMethodBody(jitName, jmd.standardMD,
                ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC, code -> {
            int   ctxSlot   = code.parameterSlot(jmd.standardCtx());
            int   arraySlot = code.parameterSlot(jmd.getImplicitParamCount());
            Label validSize = code.newLabel();

            code.aload(arraySlot)
                .aload(ctxSlot)
                .invokevirtual(CD_Array, "size$get$p", MethodTypeDesc.of(CD_long, CD_Ctx))
                .loadConstant(expectedSize)
                .lcmp()
                .ifeq(validSize);
            if (thisType.isA(pool.typeFPNumber())) {
                throwOutOfBounds(code, "", ctxSlot);
            } else {
                throwException(code, ClassDesc.of("org.xtclang.ecstasy.Assertion"), "", ctxSlot);
            }
            code.labelBinding(validSize);

            String name = thisType.getSingleUnderlyingClass(false).getName();
            switch (name) {
            case "Bit", "Nibble", "UInt8", "UInt16", "UInt32", "Dec32", "Int32":
                loadConstructorLong(code, ctxSlot, arraySlot, arrayCD, isBitArray, 0, bitLength);
                code.loadConstant(64 - bitLength)
                    .lushr()
                    .l2i();
                break;

            case "Int8":
                loadConstructorLong(code, ctxSlot, arraySlot, arrayCD, isBitArray, 0, bitLength);
                code.loadConstant(56)
                    .lushr()
                    .l2i()
                    .i2b();
                break;

            case "Int16":
                loadConstructorLong(code, ctxSlot, arraySlot, arrayCD, isBitArray, 0, bitLength);
                code.loadConstant(48)
                    .lushr()
                    .l2i()
                    .i2s();
                break;

            case "Dec64", "Int64", "UInt64":
                loadConstructorLong(code, ctxSlot, arraySlot, arrayCD, isBitArray, 0, bitLength);
                break;

            case "Dec128", "Int128", "UInt128":
                // the optimized representation places the low word before the high word
                loadConstructorLong(code, ctxSlot, arraySlot, arrayCD, isBitArray, 1, bitLength);
                loadConstructorLong(code, ctxSlot, arraySlot, arrayCD, isBitArray, 0, bitLength);
                break;

            case "Float16":
                loadConstructorLong(code, ctxSlot, arraySlot, arrayCD, isBitArray, 0, bitLength);
                code.loadConstant(48)
                    .lushr()
                    .l2i()
                    .i2s()
                    .invokestatic(CD_JavaFloat, "float16ToFloat",
                            MethodTypeDesc.of(CD_float, CD_short));
                break;

            case "Float32":
                loadConstructorLong(code, ctxSlot, arraySlot, arrayCD, isBitArray, 0, bitLength);
                code.loadConstant(32)
                    .lushr()
                    .l2i()
                    .invokestatic(CD_JavaFloat, "intBitsToFloat",
                            MethodTypeDesc.of(CD_float, CD_int));
                break;

            case "Float64":
                loadConstructorLong(code, ctxSlot, arraySlot, arrayCD, isBitArray, 0, bitLength);
                code.invokestatic(CD_JavaDouble, "longBitsToDouble",
                        MethodTypeDesc.of(CD_double, CD_long));
                break;

            default:
                throw new UnsupportedOperationException("Unsupported number type " + name);
            }
            box(code, thisType);
            code.areturn();
        });
    }

    /**
     * Load a 64-bit segment of the representation supplied to a primitive Number constructor.
     */
    private void loadConstructorLong(CodeBuilder code, int ctxSlot, int arraySlot,
                                     ClassDesc arrayCD, boolean isBitArray, int index, int bitLength) {
        code.aload(arraySlot)
            .aload(ctxSlot)
            .loadConstant(index);

        MethodTypeDesc md;
        if (isBitArray) {
            md = MethodTypeDesc.of(CD_long, CD_Ctx, CD_int);
        } else {
            code.loadConstant(bitLength);
            md = MethodTypeDesc.of(CD_long, CD_Ctx, CD_int, CD_int);
        }
        code.invokevirtual(arrayCD, "$toLong", md);
    }

    /**
     * Generate a static primitive property accessor for a property using the specified generator
     * function. Optionally generate an instance wrapper method that calls the static accessor.
     */
    protected void assemblePrimitivePropertyAccessor(ClassBuilder classBuilder, PropertyInfo prop,
            PropertyInfo declaredProp, BiConsumer<CodeBuilder, JitMethodDesc> generator) {

        JitMethodDesc jmd   = prop.getGetterJitDesc(this, thisType);
        String        name  = prop.ensureGetterJitMethodName(typeSystem);
        int           flags = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC;

        // we are dealing with JIT primitives, so we should be generating a static primitive
        // property accessor method
        assert jmd.isOptimized && jmd.isOptimizedStatic;

        generatePrimitiveGetterWrapper(classBuilder, declaredProp, name, jmd);

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
    protected void generatePrimitiveGetterWrapper(ClassBuilder classBuilder,
            PropertyInfo declaredProp, String methodName, JitMethodDesc jmd) {
        TypeConstant   parentType = declaredProp.getIdentity().getParentConstant().getType();
        JitMethodDesc  jmdWrapper = declaredProp.getGetterJitDesc(this, parentType);
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
            int ctxSlot = code.parameterSlot(jmdWrapper.isOptimized
                    ? jmdWrapper.optimizedCtx()
                    : jmdWrapper.standardCtx());

            code.aload(0);
            unbox(code, thisType);
            code.aload(ctxSlot);
            code.invokestatic(CD_this, methodName + OPT, jmd.optimizedMD);
            ClassDesc cdOptRet = jmd.optimizedMD.returnType();
            if (cdRet.equals(cdOptRet)) {
                addReturn(code, cdRet);
            } else {
                assert !cdRet.isPrimitive() && cdOptRet.isPrimitive();
                loadOptimizedReturnsToStack(code, jmd, ctxSlot);
                box(code, jmd.getOptimizedReturn(0).type);
                code.areturn();
            }
        });
    }

    /**
     * Assemble an optimized static implementation of "bits$get$p()".
     *
     * {@code return ArrayBit.fromLongs(bitLength, rawBits);}
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
        loadLongValues(code, jmd);

        // the populated long array is on the top of the stack
        // create the ArrayᐸBitᐳ and return it
        MethodTypeDesc md = MethodTypeDesc.of(CD_ArrayBit, CD_Ctx, CD_long, CD_long.arrayType());
        code.invokestatic(CD_ArrayBit, "$fromLongs", md)
            .areturn();
    }

    /**
     * Load the primitive Number representation as a Java long array.
     */
    protected void loadLongValues(CodeBuilder code, JitMethodDesc jmd) {
        int    bitLength = getBitLength();
        int    size      = jmd.optimizedCtx();
        String name      = thisType.getSingleUnderlyingClass(false).getName();

        code.loadConstant(size)
            .newarray(TypeKind.LONG);

        // the long[] is duplicated in the top stack slot
        // populate the array with the params converted to longs
        for (int i = 0; i < size; i++) {
            // duplicate the array on the stack and load the index to the stack
            code.dup()
                .loadConstant(i);
            // loat the parameter, converting to a primitive long
            // optimized multi-segment values are low-first; array storage is high-first
            int       paramIndex = size == 1 ? i : size - i - 1;
            int       slot       = code.parameterSlot(paramIndex);
            ClassDesc cd         = jmd.optimizedParams[paramIndex].cd;
            switch (cd.descriptorString()) {
            case "I", "S", "B", "Z":
                code.iload(slot)
                    .i2l();
                break;
            case "J":
                code.lload(slot);
                break;
            case "F":
                code.fload(slot);
                if (name.equals("Float16")) {
                    code.invokestatic(CD_JavaFloat, "floatToFloat16",
                                    MethodTypeDesc.of(CD_short, CD_float))
                        .i2l();
                } else {
                    code.invokestatic(CD_JavaFloat, "floatToRawIntBits",
                                    MethodTypeDesc.of(CD_int, CD_float));
                    if (name.equals("BFloat16")) {
                        code.loadConstant(16)
                            .iushr();
                    }
                    code.i2l();
                }
                break;
            case "D":
                code.dload(slot)
                    .invokestatic(CD_JavaDouble, "doubleToRawLongBits",
                            MethodTypeDesc.of(CD_long, CD_double));
                break;
            default:
                throw new IllegalStateException();
            }

            if (size == 1 && bitLength < Long.SIZE) {
                code.loadConstant(Long.SIZE - bitLength)
                    .lshl();
            }
            // store the long into the array
            code.lastore();
        }
    }

    // ----- properties ----------------------------------------------------------------------------

    /**
     * Assemble an optimized static implementation of "bitLength$get$p()".
     */
    protected void generateBitLengthGet(CodeBuilder code, JitMethodDesc jmd) {
        long bitLength = getBitLength();
        code.loadConstant(bitLength)
            .lreturn();
    }

    /**
     * Assemble an optimized static implementation of "byteLength$get$p()".
     */
    protected void generateByteLengthGet(CodeBuilder code, JitMethodDesc jmd) {
        long bitLength = getBitLength();
        code.loadConstant((bitLength + 7) / 8L)
            .lreturn();
    }

    /**
     * Assemble an optimized static implementation of "signed$get$p()".
     */
    protected void generateSignedGet(CodeBuilder code, JitMethodDesc jmd) {
        boolean signed = !thisType.isA(pool().typeUIntNumber());
        code.loadConstant(signed ? 1 : 0)
            .ireturn();
    }

    /**
     * Assemble an optimized static implementation of "sign$get$p()".
     *
     * {@code return value == 0 ? Zero : value < 0 ? Negative : Positive;}
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
     * Assemble an optimized static implementation of "negative$get$p()".
     *
     * {@code return value < 0;}
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
     * Assemble an optimized static implementation of "finite$get$p()".
     *
     * {@code return !isBinaryFP || Float.isFinite(value);}
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
     * Assemble an optimized static implementation of "infinity$get$p()".
     *
     * {@code return isBinaryFP && Float.isInfinite(value);}
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
     * Assemble an optimized static implementation of "NaN$get$p()".
     *
     * {@code return isBinaryFP && Float.isNaN(value);}
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
     * Assemble an optimized static implementation of "magnitude$get$p()".
     *
     * {@code return isUnsigned ? value : Math.abs(value);}
     */
    protected void generateMagnitudeGet(CodeBuilder code, JitMethodDesc jmd) {
        String    name      = thisType.getSingleUnderlyingClass(false).getName();
        int       paramSlot = code.parameterSlot(0);
        ClassDesc cdMath    = ClassDesc.of(Math.class.getName());

        switch (name) {
        case "Int8", "Int16", "Int32":
            code.iload(paramSlot)
                .invokestatic(cdMath, "abs", MethodTypeDesc.of(CD_int, CD_int));
            generateMagnitudeReturn(code, jmd);
            break;

        case "Bit", "Nibble", "UInt8", "UInt16", "UInt32":
            code.iload(paramSlot);
            generateMagnitudeReturn(code, jmd);
            break;

        case "Int64":
            code.lload(paramSlot)
                .invokestatic(cdMath, "abs", MethodTypeDesc.of(CD_long, CD_long));
            generateMagnitudeReturn(code, jmd);
            break;

        case "UInt64":
            code.lload(paramSlot);
            generateMagnitudeReturn(code, jmd);
            break;

        case "Float16", "Float32":
            code.fload(paramSlot)
                .invokestatic(cdMath, "abs", MethodTypeDesc.of(CD_float, CD_float));
            generateMagnitudeReturn(code, jmd);
            break;

        case "Float64":
            code.dload(paramSlot)
                .invokestatic(cdMath, "abs", MethodTypeDesc.of(CD_double, CD_double));
            generateMagnitudeReturn(code, jmd);
            break;

        case "Dec32":
            code.iload(paramSlot);
            code.aload(code.parameterSlot(jmd.optimizedCtx()));
            code.invokestatic(art.CD(), "abs$p",
                    MethodTypeDesc.of(CD_int, CD_int, CD_Ctx));
            generateMagnitudeReturn(code, jmd);
            break;

        case "Dec64":
            code.lload(paramSlot);
            code.aload(code.parameterSlot(jmd.optimizedCtx()));
            code.invokestatic(art.CD(), "abs$p",
                    MethodTypeDesc.of(CD_long, CD_long, CD_Ctx));
            generateMagnitudeReturn(code, jmd);
            break;

        case "Dec128", "Int128":
            code.lload(code.parameterSlot(0));
            code.lload(code.parameterSlot(1));
            code.aload(code.parameterSlot(jmd.optimizedCtx()));
            code.invokestatic(art.CD(), "abs$p",
                    MethodTypeDesc.of(CD_long, CD_long, CD_long, CD_Ctx));
            if (jmd.optimizedMD.returnType().isPrimitive()) {
                code.lreturn();
            } else {
                loadFromContext(code, CD_long, 0, code.parameterSlot(jmd.optimizedCtx()));
                box(code, thisType);
                code.areturn();
            }
            break;

        case "UInt128":
            if (jmd.optimizedMD.returnType().isPrimitive()) {
                code.lload(code.parameterSlot(1));
                storeToContext(code, CD_long, 0, code.parameterSlot(jmd.optimizedCtx()));
                code.lload(code.parameterSlot(0))
                    .lreturn();
            } else {
                code.lload(code.parameterSlot(0));
                code.lload(code.parameterSlot(1));
                box(code, thisType);
                code.areturn();
            }
            break;

        default:
            throw new UnsupportedOperationException("Unsupported number type " + name);
        }
    }

    /**
     * Return the single primitive value on the stack, boxing it when required by the accessor's
     * declared return type.
     */
    protected void generateMagnitudeReturn(CodeBuilder code, JitMethodDesc jmd) {
        ClassDesc returnCD = jmd.optimizedMD.returnType();
        if (returnCD.isPrimitive()) {
            addReturn(code, returnCD);
        } else {
            box(code, thisType);
            code.areturn();
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

    // ----- methods -------------------------------------------------------------------------------

    /**
     * Assemble optimized static implementations of "toBitArray$p()", "toNibbleArray$p()", and
     * "toByteArray$p()".
     *
     * {@code return Array.fromLongs(mutability, bitLength, rawBits);}
     */
    protected void generateToArray(CodeBuilder code, JitMethodDesc jmd, String jitName) {
        long      bitLength = getBitLength();
        long      size;
        ClassDesc arrayCD;

        switch (jitName) {
        case "toBitArray":
            arrayCD = CD_ArrayBit;
            size    = bitLength;
            break;

        case "toNibbleArray":
            arrayCD = CD_ArrayNibble;
            size    = bitLength;
            break;

        case "toByteArray":
            arrayCD = CD_ArrayUInt8;
            size    = bitLength >>> 3;
            break;

        default:
            throw new IllegalArgumentException(jitName);
        }

        int       mutabilityIndex = jmd.getOptimizedParamIndex(0);
        int       mutabilitySlot  = code.parameterSlot(jmd.getImplicitParamCount() + mutabilityIndex);
        ClassDesc mutabilityCD    = jmd.optimizedParams[mutabilityIndex].cd;

        code.aload(code.parameterSlot(jmd.optimizedCtx()))
                .aload(mutabilitySlot)
                .loadConstant(size);

        loadLongValues(code, jmd);

        code.invokestatic(arrayCD, "$fromLongs",
                        MethodTypeDesc.of(arrayCD, CD_Ctx, mutabilityCD, CD_long, CD_long.arrayType()))
                .areturn();
    }
}
