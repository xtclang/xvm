package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitTypeDesc;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_int;
import static java.lang.constant.ConstantDescs.CD_long;

import static org.xvm.javajit.Builder.CD_nType;
import static org.xvm.javajit.Builder.OPT;
import static org.xvm.javajit.Builder.CD_ArrayObj;
import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_Enumeration;
import static org.xvm.javajit.Builder.CD_Object;
import static org.xvm.javajit.Builder.CD_nEnum;
import static org.xvm.javajit.Builder.Instance;
import static org.xvm.javajit.Builder.md;

/**
 * A "mixin" interface to generate bytecodes for operations on Ecstasy enum types.
 */
public interface EnumBuilderSupport {
    /**
     * Assemble the class-specific attributes for the enum.
     */
    default void assembleEnumClass(CommonBuilder builder, ClassBuilder classBuilder) {
        ClassDesc sequentialCD = builder.ensureClassDesc(builder.pool().typeSequential());

        classBuilder.withSuperclass(builder.getSuperCD())
                .withInterfaceSymbols(sequentialCD)
                .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT);
    }

    /**
     * Assemble the properties for the enum.
     *
     * <p>The property getter methods generated for the enum will be abstract, Concrete
     * implementations of these methods are generated for individual enum values by the enum
     * value builders.
     */
    default void assembleEnumProperties(CommonBuilder builder, ClassBuilder classBuilder) {
        assembleEnumerationProp(builder, classBuilder);
        assembleNameProp(builder, classBuilder);
        assembleOrdinalProp(builder, classBuilder);
    }

    /**
     * Assemble the enumeration property:
     * <p>Enumeration<Enum> enumeration;
     */
    default void assembleEnumerationProp(CommonBuilder builder, ClassBuilder classBuilder) {
        PropertyInfo   prop = builder.typeInfo.findProperty("enumeration");
        String         name = prop.ensureGetterJitMethodName(builder.typeSystem);
        JitMethodDesc  jmd  = prop.getGetterJitDesc(builder);

        if (builder.isNativeMethod(name, jmd.standardMD)) {
            return;
        }

        TypeConstant enumType = builder.thisId.getValueType(builder.pool(), null);
        String       enumName = builder.ensureJitClassName(enumType);
        ClassDesc    enumCD   = ClassDesc.of(enumName);

        classBuilder.withMethodBody(name, jmd.standardMD, ClassFile.ACC_PUBLIC, code ->
                code.getstatic(enumCD, Instance, enumCD)
                        .areturn());
    }

    /**
     * Assemble the ordinal property:
     * <p>Int ordinal;
     */
    default void assembleOrdinalProp(CommonBuilder builder, ClassBuilder classBuilder) {
        PropertyInfo   prop = builder.typeInfo.findProperty("ordinal");
        String         name = prop.ensureGetterJitMethodName(builder.typeSystem);
        JitMethodDesc  jmd  = prop.getGetterJitDesc(builder);

        if (!builder.isNativeMethod(name, jmd.standardMD)) {
            builder.assembleMethodWrapper(classBuilder, name, jmd);
        }

        String optName = name + OPT;
        if (!builder.isNativeMethod(optName, jmd.optimizedMD)) {
            classBuilder.withMethod(optName, jmd.optimizedMD,
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT, ignored -> {});
        }
    }

    /**
     * Assemble the name property:
     * <p>String name;
     */
    default void assembleNameProp(CommonBuilder builder, ClassBuilder classBuilder) {
        PropertyInfo   prop = builder.typeInfo.findProperty("name");
        String         name = prop.ensureGetterJitMethodName(builder.typeSystem);
        JitMethodDesc  jmd  = prop.getGetterJitDesc(builder);

        if (builder.isNativeMethod(name, jmd.standardMD)) {
            return;
        }

        classBuilder.withMethod(name, jmd.standardMD,
                ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT, ignored -> {});
    }

    /**
     * Assemble methods for the enum.
     */
    default void assembleEnumMethods(CommonBuilder builder, ClassBuilder classBuilder) {
        generateOrderable(builder, classBuilder);
        generateSequential(builder, classBuilder);
    }

    /**
     * Assemble the Orderable implementation methods.
     */
    default void generateOrderable(CommonBuilder builder, ClassBuilder classBuilder) {
        ConstantPool      pool      = builder.pool();
        boolean           isBoolean = builder.jitType.equals(pool.typeBoolean());
        SignatureConstant eqSig     = pool.sigEquals();
        MethodInfo        eqMethod  = builder.typeInfo.getMethodBySignature(eqSig);
        JitMethodDesc     eqJmd     = eqMethod.getJitDesc(builder);
        int               flags     = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC;

        // Boolean is **the only** optimized type that is an Enum; treat is separately
        classBuilder.withMethodBody(eqSig.getName()+OPT, eqJmd.optimizedMD, flags,
                isBoolean ? this::assembleBooleanEquals : this::assembleEquals);

        SignatureConstant cmpSig    = pool.sigCompare();
        MethodInfo        cmpMethod = builder.typeInfo.getMethodBySignature(cmpSig);
        JitMethodDesc     cmpJmd    = cmpMethod.getJitDesc(builder);
        String            cmpName   = cmpSig.getName();

        if (isBoolean) {
            assert cmpJmd.isOptimized;
            builder.assembleMethodWrapper(classBuilder, cmpName, cmpJmd);
            classBuilder.withMethodBody(cmpName+OPT, cmpJmd.optimizedMD, flags,
                    code -> assembleBooleanCompare(builder, code));
        } else {
            assert !cmpJmd.isOptimized;
            classBuilder.withMethodBody(cmpName, cmpJmd.standardMD, flags,
                    code -> assembleCompare(builder, code));
        }
    }

    /**
     * The signature of the function we generate is:
     * <p>"public boolean equals$p(Ctx ctx, nType CompileType, [EnumType] o1, {EnumType} o2)"
     */
    default void assembleEquals(CodeBuilder code) {
        // all we need is to call the equivalent function on nEnum
        code.aload(0)
            .aload(1)
            .aload(2)
            .aload(3)
            .invokestatic(CD_nEnum, "equals$p",
                    md(CD_boolean, CD_Ctx, CD_nType, CD_nEnum, CD_nEnum))
            .ireturn();
    }

    /**
     * Generate primitive Boolean equality.
     */
    default void assembleBooleanEquals(CodeBuilder code) {
        Label notEqual = code.newLabel();
        code.iload(2)
            .iload(3)
            .if_icmpne(notEqual)
            .iconst_1()
            .ireturn()
            .labelBinding(notEqual)
            .iconst_0()
            .ireturn();
    }

    /**
     * The signature of the function we generate is:
     * <p>"public Ordered compare(Ctx ctx, nType CompileType, [EnumType] o1, {EnumType} o2)"
     */
    default void assembleCompare(CommonBuilder builder, CodeBuilder code) {
        // there is a custom primitivized function on nEnum:
        //      long compare$p(Ctx ctx, nType CompileType, nEnum o1, nEnum o2)
        // which returns a negative, zero or positive value that needs to be translated into
        // the corresponding Ordered value using the Builder.returnIntToOrdered method

        // long c = nEnum.compare$p(ctx, CompileType, o1, o2);
        code.aload(0)
            .aload(1)
            .aload(2)
            .aload(3)
            .invokestatic(CD_nEnum, "compare$p",
                    md(CD_long, CD_Ctx, CD_nType, CD_nEnum, CD_nEnum))
            .l2i();
        builder.returnIntToOrdered(code);
    }

    /**
     * Generate primitive Boolean comparison.
     */
    default void assembleBooleanCompare(CommonBuilder builder, CodeBuilder code) {
        code.iload(2)
            .iload(3)
            .isub();
        builder.returnIntToOrdered(code);
    }

    /**
     * Assemble the Sequential implementation methods.
     */
    default void generateSequential(CommonBuilder builder, ClassBuilder classBuilder) {
        TypeConstant type = builder.pool().typeSequential();
        TypeInfo     info = type.ensureTypeInfo();

        for (MethodInfo method : info.getMethods().values()) {
            if (!method.isAbstract() || !method.getIdentity().getNamespace().getType().equals(type)) {
                continue;
            }
            String        name    = method.getSignature().getName();
            JitMethodDesc jmd     = method.getJitDesc(builder, builder.thisType);
            String        jitName = method.ensureJitMethodName(builder.typeSystem);
            switch (name) {
                case "next":
                    assembleNext(builder, classBuilder, jmd, jitName);
                    break;
                case "prev":
                    assemblePrev(builder, classBuilder, jmd);
                    break;
                case "skip":
                    assembleSkip(builder, classBuilder, jmd, jitName);
                    break;
                case "stepsTo":
                    assembleStepsTo(builder, classBuilder, jmd, jitName);
                    break;
                default:
                    throw new UnsupportedOperationException("Unsupported Sequential method: " + name);
            }
        }
    }

    /**
     * Assemble the Sequential next() method.
     */
    default void assembleNext(CommonBuilder builder, ClassBuilder classBuilder, JitMethodDesc jmd,
                              String jitName) {
        if (!builder.isNativeMethod(jitName, jmd.standardMD)) {
            builder.assembleMethodWrapper(classBuilder, jitName, jmd);
        }

        String optimizedName = jitName + OPT;
        if (!builder.isNativeMethod(optimizedName, jmd.optimizedMD)) {
            if (builder.thisType.isJitPrimitive()) {
                ClassDesc cd = jmd.optimizedMD.parameterType(0);
                switch (cd.descriptorString()) {
                    case "B", "I", "S", "Z":
                        assemblePrimitiveIntNext(builder, classBuilder, jmd, optimizedName);
                        break;

                    default:
                        throw new UnsupportedOperationException("Unsupported JIT primitive type "
                                + builder.thisType.ensureJitClassName(builder.typeSystem));
                }
            } else {
                classBuilder.withMethod(optimizedName, jmd.optimizedMD,
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT, ignored -> {});
            }
        }
    }

    /**
     * Generate the code for an optimized static primitive next() method where the JIT primitive
     * is a Java {@code int} with a signature:
     * <p>public static boolean next$p(int thi$, Ctx ctx)
     */
    default void assemblePrimitiveIntNext(CommonBuilder builder, ClassBuilder classBuilder,
                                          JitMethodDesc jmd, String jitName) {
        int flags = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC;

        classBuilder.withMethodBody(jitName, jmd.optimizedMD, flags, code -> {
            Label labelLT    = code.newLabel();
            int   thisSlot   = code.parameterSlot(0);
            int   ctxSlot    = code.parameterSlot(1);
            int   maxOrdinal = getValueCount(builder) - 1;

            code.iload(thisSlot)                       // load the thi$ primitive
                .loadConstant(maxOrdinal)              // load the max ordinal
                .if_icmplt(labelLT)                    // compare thi$ to max ordinal
                .iconst_0()                            // thi$ >= max ordinal
                .ireturn()                             // return false
                .labelBinding(labelLT)                 // jump here for thi$ < max ordinal
                .aload(ctxSlot)                        // load the Ctx
                .iload(thisSlot)                       // load thi$
                .iconst_1()                            // add 1 to thi$
                .iadd()
                .i2l()                                 // convert to long
                .putfield(CD_Ctx, "i0", CD_long) // set next value into Ctx.i0 field
                .iconst_1()                            // return true
                .ireturn();
        });
    }

    /**
     * Assemble the Sequential prev() method.
     */
    default void assemblePrev(CommonBuilder builder, ClassBuilder classBuilder, JitMethodDesc jmd) {
        String standardName  = "prev";
        String optimizedName = standardName + OPT;

        if (!builder.isNativeMethod(standardName, jmd.standardMD)) {
            builder.assembleMethodWrapper(classBuilder, standardName, jmd);
        }

        if (!builder.isNativeMethod(optimizedName, jmd.optimizedMD)) {
            if (builder.thisType.isJitPrimitive()) {
                ClassDesc cd = jmd.optimizedMD.parameterType(0);
                switch (cd.descriptorString()) {
                    case "B", "I", "S", "Z":
                        assemblePrimitiveIntPrev(classBuilder, jmd, optimizedName);
                        break;

                    default:
                        throw new UnsupportedOperationException("Unsupported JIT primitive type "
                                + builder.thisType.ensureJitClassName(builder.typeSystem));
                }
            } else {
                classBuilder.withMethod(optimizedName, jmd.optimizedMD,
                        ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT, ignored -> {});
            }
        }
    }

    /**
     * Generate the code for an optimized static primitive prev() method where the JIT primitive
     * is a Java {@code int} with a signature:
     * <p>public static boolean prev$p(int thi$, Ctx ctx)
     */
    default void assemblePrimitiveIntPrev(ClassBuilder classBuilder,
                                          JitMethodDesc jmd, String jitName) {
        int flags = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC;

        classBuilder.withMethodBody(jitName, jmd.optimizedMD, flags, code -> {
            Label labelZero = code.newLabel();
            int   thisSlot  = code.parameterSlot(0);
            int   ctxSlot   = code.parameterSlot(1);

            code.aload(ctxSlot)                        // load the Ctx
                .iload(thisSlot)                       // load the thi$ primitive
                .dup()                                 // duplicate thi$ on the stack
                .ifle(labelZero)                       // if thi$ is zero, jump to labelZero
                .iconst_1()                            // there is a prev
                .isub()                                // subtract 1 from thi$
                .i2l()                                 // convert to a primitive long
                .putfield(CD_Ctx, "i0", CD_long) // set the prev value into the ctx field i0
                .iconst_1()
                .ireturn()                             // return true (the result is in Ctx.i0)
                .labelBinding(labelZero)               // thi$ is <= zero, so no prev value
                .iconst_0()
                .ireturn();                            // return false
        });
    }

    /**
     * Assemble the Sequential "skip(Int steps)" method.
     */
    default void assembleSkip(CommonBuilder builder, ClassBuilder classBuilder, JitMethodDesc jmd,
                              String jitName) {
        String optimizedName = jitName + OPT;

        if (!builder.isNativeMethod(jitName, jmd.standardMD)) {
            builder.assembleMethodWrapper(classBuilder, jitName, jmd);
        }

        if (!builder.isNativeMethod(optimizedName, jmd.optimizedMD)) {
            if (builder.thisType.isJitPrimitive()) {
                ClassDesc cd = jmd.optimizedMD.parameterType(0);
                switch (cd.descriptorString()) {
                    case "B", "I", "S", "Z":
                        assemblePrimitiveIntSkip(builder, classBuilder, jmd, optimizedName);
                        break;

                    default:
                        throw new UnsupportedOperationException("Unsupported JIT primitive type "
                                + builder.thisType.ensureJitClassName(builder.typeSystem));
                }
            } else {
                assembleEnumSkip(builder, classBuilder, jmd, optimizedName);
            }
        }
    }

    /**
     * Generate the code for an optimized static primitive skip() method where the JIT primitive
     * is a Java {@code int} with a signature:
     * <p>public static int skip$p(int thi$, Ctx ctx, long steps)
     */
    default void assemblePrimitiveIntSkip(CommonBuilder builder, ClassBuilder classBuilder,
                                          JitMethodDesc jmd, String jitName) {
        MethodTypeDesc mdHelper = MethodTypeDesc.of(CD_int, CD_int, CD_Ctx, CD_long, CD_int);
        int            flags    = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC;

        classBuilder.withMethodBody(jitName, jmd.optimizedMD, flags, code -> {
            int thisSlot   = code.parameterSlot(0);
            int ctxSlot    = code.parameterSlot(1);
            int stepsSlot  = code.parameterSlot(2);
            int valueCount = getValueCount(builder);

            code.iload(thisSlot)                                  // load thi$
                .aload(ctxSlot)                                   // load Ctx
                .lload(stepsSlot)                                 // load the steps arg
                .loadConstant(valueCount)                         // load the number of enum values
                .invokestatic(CD_nEnum, "$skip", mdHelper)  // call the helper
                .ireturn();                                       // return the result
        });
    }

    /**
     * Generate the code for an optimized skip() method for a non-primitive enum with a signature:
     * <p>public static Enum skip$p(Ctx ctx, long steps)
     */
    default void assembleEnumSkip(CommonBuilder builder, ClassBuilder classBuilder,
                                  JitMethodDesc jmd, String jitName) {
        MethodTypeDesc mdEnum     = MethodTypeDesc.of(CD_Enumeration, CD_Ctx);
        MethodTypeDesc mdValues   = MethodTypeDesc.of(CD_ArrayObj, CD_Ctx);
        MethodTypeDesc mdOrdinal  = MethodTypeDesc.of(CD_long, CD_Ctx);
        MethodTypeDesc mdHelper   = MethodTypeDesc.of(CD_long, CD_long, CD_Ctx, CD_long, CD_int);
        MethodTypeDesc mdGetElem  = MethodTypeDesc.of(CD_Object, CD_Ctx, CD_long);
        int            flags      = ClassFile.ACC_PUBLIC;
        ClassDesc      thisCD     = builder.art.CD();
        int            valueCount = getValueCount(builder);

        classBuilder.withMethodBody(jitName, jmd.optimizedMD, flags, code -> {
            int thisSLot  = 0;
            int ctxSlot   = code.parameterSlot(0);
            int stepsSlot = code.parameterSlot(1);

            code.aload(thisSLot)  // load this
                .aload(ctxSlot)   // load the Ctx
                .invokevirtual(thisCD, "enumeration$get", mdEnum) // get enumeration
                .aload(ctxSlot)   // load the Ctx
                .invokevirtual(CD_Enumeration, "values$get", mdValues) // get values array
                .aload(ctxSlot)   // load the Ctx
                .aload(thisSLot)  // load this
                .aload(ctxSlot)   // load ctx
                .invokevirtual(thisCD, "ordinal$get$p", mdOrdinal) // get this ordinal
                .aload(ctxSlot)   // load the Ctx
                .lload(stepsSlot) // load the steps arg
                .loadConstant(valueCount)  // load the number of enum values
                .invokestatic(CD_nEnum, "$skip", mdHelper)  // call the helper (may throw OOB)
                // the stack contains: enum values array, ctx, index (index will be valid)
                .invokevirtual(CD_ArrayObj, "getElement$p", mdGetElem) // get the enum value
                .checkcast(thisCD)   // cast to this Enum type
                .areturn();          // return the result
        });
    }

    /**
     * Assemble the Sequential "long stepsTo$p(Enum that)" method.
     */
    default void assembleStepsTo(CommonBuilder builder, ClassBuilder classBuilder,
                                 JitMethodDesc jmd, String jitName) {
        String optimizedName = jitName + OPT;

        if (!builder.isNativeMethod(jitName, jmd.standardMD)) {
            builder.assembleMethodWrapper(classBuilder, jitName, jmd);
        }

        if (!builder.isNativeMethod(optimizedName, jmd.optimizedMD)) {
            if (builder.thisType.isJitPrimitive()) {
                ClassDesc cd = jmd.optimizedMD.parameterType(0);
                switch (cd.descriptorString()) {
                    case "B", "I", "S", "Z":
                        assemblePrimitiveIntStepsTo(classBuilder, jmd, optimizedName);
                        break;

                    default:
                        throw new UnsupportedOperationException("Unsupported JIT primitive type "
                                + builder.thisType.ensureJitClassName(builder.typeSystem));
                }
            } else {
                int flags = ClassFile.ACC_PUBLIC;
                classBuilder.withMethodBody(optimizedName, jmd.optimizedMD, flags, code -> {
                    MethodTypeDesc mdOrdinal = MethodTypeDesc.of(CD_long, CD_Ctx);
                    ClassDesc      thisCD    = builder.art.CD();
                    int            thisSLot  = 0;
                    int            ctxSlot   = code.parameterSlot(0);
                    int            thatSlot  = code.parameterSlot(1);

                    code.aload(thatSlot)
                        .checkcast(thisCD)
                        .aload(ctxSlot)
                        .invokevirtual(thisCD, "ordinal$get$p", mdOrdinal)
                        .aload(thisSLot)
                        .aload(ctxSlot)
                        .invokevirtual(thisCD, "ordinal$get$p", mdOrdinal)
                        .lsub()
                        .lreturn();
                });
            }
        }
    }

    /**
     * Generate the code for an optimized static primitive stepsTo() method where the JIT primitive
     * is a Java {@code int} with a signature:
     * <p>public static long stepsTo$p(int thi$, Ctx ctx, int that)
     */
    default void assemblePrimitiveIntStepsTo(ClassBuilder classBuilder, JitMethodDesc jmd, String jitName) {
        int flags = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC;

        classBuilder.withMethodBody(jitName, jmd.optimizedMD, flags, code -> {
            int thisSLot = code.parameterSlot(0);
            int thatSlot = code.parameterSlot(2);

            code.iload(thatSlot)  // load the "that" arg
                .iload(thisSLot)  // load thi$
                .isub()           // the result is that - thi$
                .i2l()            // convert to long (Int64)
                .lreturn();       // return the result
        });
    }

    /**
     * @return the number of enum values for the enum being built
     */
    default int getValueCount(CommonBuilder builder) {
        return EnumerationBuilderSupport.getEnumValues(builder.classStruct).length;
    }
}
