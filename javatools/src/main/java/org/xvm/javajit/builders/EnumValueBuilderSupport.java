package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.ClassStructure;

import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.TypeSystem;

import static java.lang.constant.ConstantDescs.CD_boolean;

import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_JavaObject;
import static org.xvm.javajit.Builder.CD_String;
import static org.xvm.javajit.Builder.Instance;
import static org.xvm.javajit.Builder.MD_StringOf;
import static org.xvm.javajit.Builder.OPT;

/**
 * A "mixin" interface to generate bytecodes for operations on Ecstasy enum value types.
 */
public interface EnumValueBuilderSupport {
    /**
     * @return the ordinal of this enum value
     */
    int getOrdinal();

    /**
     * @return the {@link ClassDesc} of the next enum value or {@code null} if there is no next
     * enum value
     */
    ClassDesc getNextCD();

    /**
     * @return the {@link ClassDesc} of the previous enum value or {@code null} if there is no
     * previous enum value
     */
    ClassDesc getPrevCD();

    /**
     * Calculate the ordinal of an enum value.
     *
     * @param classStruct  the {@link ClassStructure} of the enum value
     * @param enumValues   the array of enum value {@link ClassStructure}instances for the enum
     *
     * @return the ordinal of the enum value
     */
    static int calculateOrdinal(ClassStructure classStruct, ClassStructure[] enumValues) {
        int ord = -1;
        for (int i = 0; i < enumValues.length; i++) {
            if (enumValues[i] == classStruct) {
                ord = i;
                break;
            }
        }
        assert ord >= 0;
        return ord;
    }

    /**
     * @return the {@link ClassDesc} of the previous enum value, or {@code null} if this is the
     *         first value for the parent enum.
     */
    static ClassDesc calculatePrevCD(CommonBuilder builder, ClassStructure[] enumValues, int ordinal) {
        if (ordinal > 0) {
            return cdForClass(enumValues[ordinal - 1], builder.typeSystem);
        }
        return null;
    }

    /**
     * @return the {@link ClassDesc} of the next enum value, or {@code null} if this is the last
     *         value for the parent enum.
     */
    static ClassDesc calculateNextCD(CommonBuilder builder, ClassStructure[] enumValues, int ordinal) {
        if (ordinal < enumValues.length - 1) {
            return cdForClass(enumValues[ordinal + 1], builder.typeSystem);
        }
        return null;
    }

    /**
     * @return the {@link ClassDesc} of the enum value's super class
     */
    default ClassDesc getEnumValueSuperCD(CommonBuilder builder) {
        TypeConstant parent = ((ClassStructure) builder.classStruct.getParent()).getFormalType();
        String       name   = parent.ensureJitClassName(builder.typeSystem);
        return ClassDesc.of(name);
    }

    /**
     * Apply any modifications to the enum value class.
     */
    default void assembleEnumValueClass(CommonBuilder builder, ClassBuilder classBuilder) {
        classBuilder.withSuperclass(getEnumValueSuperCD(builder))
                    .withFlags(ClassFile.ACC_PUBLIC);
    }

    /**
     * Append code to the enum value static initializer.
     */
    default void appendEnumValueCLInit(CommonBuilder builder, CodeBuilder code) {
        code.aconst_null()
            .loadConstant(builder.classStruct.getName())
            .invokestatic(CD_String, "of", MD_StringOf)
            .putstatic(builder.art.CD(), EnumerationBuilderSupport.FIELD_NAMES, CD_String);
    }

    /**
     * Assemble the methods for the enum value.
     */
    default void assembleEnumValueMethods(CommonBuilder builder, ClassBuilder classBuilder) {
        assembleSequentialMethods(builder, classBuilder);
    }

    /**
     * Assemble the properties for the enum value.
     */
    default void assembleEnumValueProperties(CommonBuilder builder, ClassBuilder classBuilder) {
        // public static final String $name;
        String fieldName = EnumerationBuilderSupport.FIELD_NAMES;
        if (!builder.isNativeField(fieldName)) {
            classBuilder.withField(fieldName, CD_String,
                    ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);
        }

        assembleOrdinalProp(builder, classBuilder);
        assembleNameProp(builder, classBuilder);
    }

    /**
     * Assemble the ordinal property for the enum value.
     */
    private void assembleOrdinalProp(CommonBuilder builder, ClassBuilder classBuilder) {
        PropertyInfo  prop       = builder.typeInfo.findProperty("ordinal");
        String        getterName = prop.ensureGetterJitMethodName(builder.typeSystem) + OPT;
        JitMethodDesc jmDesc     = prop.getGetterJitDesc(builder);
        int           flags      = ClassFile.ACC_PUBLIC;

        if (!builder.isNativeMethod(getterName, jmDesc.optimizedMD)) {
            classBuilder.withMethodBody(getterName, jmDesc.optimizedMD, flags, code ->
                code.loadConstant((long) getOrdinal())
                    .lreturn());
            }
    }

    /**
     * Assemble the name property for the enum value.
     */
    private void assembleNameProp(CommonBuilder builder, ClassBuilder classBuilder) {
        PropertyInfo  prop       = builder.typeInfo.findProperty("name");
        String        getterName = prop.ensureGetterJitMethodName(builder.typeSystem);
        JitMethodDesc jmDesc     = prop.getGetterJitDesc(builder);
        int           flags      = ClassFile.ACC_PUBLIC;

        if (!builder.isNativeMethod(getterName, jmDesc.optimizedMD)) {
            classBuilder.withMethodBody(getterName, jmDesc.standardMD, flags, code ->
                    code.getstatic(builder.art.CD(), EnumerationBuilderSupport.FIELD_NAMES, CD_String)
                        .areturn());
        }
    }

    /**
     * Assemble the Sequential methods for this enum value.
     */
    private void assembleSequentialMethods(CommonBuilder builder, ClassBuilder classBuilder) {
        TypeConstant type = builder.pool().typeSequential();
        TypeInfo info = type.ensureTypeInfo();
        for (MethodInfo method : info.getMethods().values()) {
            if (!method.isAbstract() || !method.getIdentity().getNamespace().getType().equals(type)) {
                continue;
            }
            String name    = method.getSignature().getName();
            String jitName = method.ensureJitMethodName(builder.typeSystem);
            switch (name) {
                case "next":
                    assembleNextOrPrev(builder, classBuilder, jitName, getNextCD());
                    break;
                case "prev":
                    assembleNextOrPrev(builder, classBuilder, jitName, getPrevCD());
                    break;
            }
        }
    }

    /**
     * Assemble the native Sequential next() or prev() methods:
     * <p>boolean next$p(Ctx ctx)
     * <p>boolean prev$p(Ctx ctx)
     *
     * <p>These methods override the corresponding abstract next() and prev() methods generated in the
     * {@link EnumBuilder} for this enum.
     */
    private void assembleNextOrPrev(CommonBuilder builder, ClassBuilder classBuilder,
                                    String methodName, ClassDesc valueCD) {
        MethodTypeDesc md      = MethodTypeDesc.of(CD_boolean, CD_Ctx);
        String         jitName = methodName + OPT;

        if (builder.isNativeMethod(jitName, md)) {
            return;
        }

        classBuilder.withMethodBody(jitName, md, ClassFile.ACC_PUBLIC, code -> {
            if (valueCD == null) {
                // no next or prev, return false
                code.loadConstant(0)
                    .ireturn();
            } else {
                // load the next or prev value to the stack
                code.getstatic(valueCD, Instance, valueCD);
                // store it in the context
                Builder.storeToContext(code, CD_JavaObject, 0);
                // return true
                code.loadConstant(1)
                    .ireturn();
            }
        });
    }

    /**
     * @return the {@link ClassDesc} for a {@link ClassStructure}
     */
    static ClassDesc cdForClass(ClassStructure clz, TypeSystem typeSystem) {
        TypeConstant type = clz.getFormalType().getJitCCType();
        String       name = type.ensureJitClassName(typeSystem);
        return ClassDesc.of(name);
    }
}
