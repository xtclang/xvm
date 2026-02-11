package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;

import java.lang.constant.ClassDesc;
import java.lang.constant.ConstantDescs;
import java.lang.constant.MethodTypeDesc;

import java.util.List;

import org.xvm.asm.Component;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.Builder;
import org.xvm.javajit.JitCtorDesc;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.TypeSystem;

import static java.lang.constant.ConstantDescs.INIT_NAME;
import static java.lang.constant.ConstantDescs.MTD_void;

/**
 * The builder for native types that uses an existing Java class to augment with the Ecstasy natural
 * code.
 */
public class AugmentingBuilder extends CommonBuilder {

    public AugmentingBuilder(TypeSystem typeSystem, TypeConstant type, ClassModel model) {
        super(typeSystem, type);

        this.model = model;
    }

    /**
     * The augmenting Classfile model.
     */
    public final ClassModel model;

    @Override
    protected ClassDesc getSuperCD() {
        return model.superclass().get().asSymbol();
    }

    @Override
    public boolean assembleImplClass(String className, ClassBuilder classBuilder) {
        // AugmentingBuilder uses the native class attributes except of the "ABSTRACT" flag
        // that is driven by the type
        int flags = model.flags().flagsMask();
        if ((flags & ClassFile.ACC_ABSTRACT) != 0 && !typeInfo.isAbstract()) {
            flags &= ~ClassFile.ACC_ABSTRACT;
        }
        classBuilder.withFlags(flags);

        // implemented interfaces may not be native; add them if necessary
        assembleImplInterfaces(classBuilder);

        // if there is any native Exception, we need to generate the "$createJavaException" method
        TypeConstant type        = typeInfo.getType();
        TypeConstant T_EXCEPTION = type.getConstantPool().typeException();
        if (type.isA(T_EXCEPTION) && !type.removeAccess().equals(T_EXCEPTION)) {
            new ExceptionBuilder(typeSystem, type).assembleCreateException(className, classBuilder);
        }

        // for now, native enum values need to be fully functional (no code gen)
        return typeInfo.getFormat() != Component.Format.ENUMVALUE;
    }

    @Override
    protected void augmentStaticInitializer(String className, CodeBuilder code) {
        MethodModel model = findMethod(ConstantDescs.CLASS_INIT_NAME, MTD_void);

        if (model != null) {
            // the native class had the static initializer, which was skipped during the "copy"
            // phase and now needs to be incorporated
            model.code().ifPresent(oldCode -> oldCode.forEach(code::with));
        }
    }

    @Override
    protected void assembleInitializer(String className, ClassBuilder classBuilder,
                                       List<PropertyInfo> props) {
        MethodModel mm = findMethod(INIT_NAME, MD_Initializer);
        if (mm == null) {
            super.assembleInitializer(className, classBuilder, props);
        }
    }

    @Override
    protected void assembleField(String className, ClassBuilder classBuilder, PropertyInfo prop) {
        String jitName = prop.getIdentity().ensureJitPropertyName(typeSystem);
        if (findField(jitName) == null) {
            super.assembleField(className, classBuilder, prop);
        }
    }

    @Override
    protected void assemblePropertyAccessor(String className, ClassBuilder classBuilder,
                                            PropertyInfo prop, String jitName, MethodTypeDesc md,
                                            boolean isOptimized, boolean isGetter) {
        MethodModel mm = findMethod(jitName, md);
        if (mm != null && (mm.flags().flagsMask() & ClassFile.ACC_ABSTRACT) == 0) {
            // the property is already copied by the NativeTypeSystem
            return;
        }

        super.assemblePropertyAccessor(className, classBuilder, prop, jitName, md, isOptimized, isGetter);
    }

    @Override
    protected void generateTrivialGetter(String className, ClassBuilder classBuilder, PropertyInfo prop) {
        if (findMethod(prop.ensureGetterJitMethodName(typeSystem), null) == null) {
            super.generateTrivialGetter(className, classBuilder, prop);
        }
    }

    @Override
    protected void generateTrivialSetter(String className, ClassBuilder classBuilder, PropertyInfo prop) {
        if (findMethod(prop.ensureSetterJitMethodName(typeSystem), null) == null) {
            super.generateTrivialSetter(className, classBuilder, prop);
        }
    }

    @Override
    protected void assembleMethod(String className, ClassBuilder classBuilder, MethodInfo method,
                                  String jitName, JitMethodDesc jmd) {
        if (method.isCtorOrValidator()) {
            String        newName = jitName.replace("construct", typeInfo.isSingleton() ? INIT : NEW);
            JitMethodDesc newJmd  = Builder.convertConstructToNew(typeInfo, ClassDesc.of(className), (JitCtorDesc) jmd);
            MethodModel   newMM   = newJmd.isOptimized
                    ? findMethod(newName+OPT, newJmd.optimizedMD)
                    : findMethod(newName, newJmd.standardMD);
            if (newMM != null) {
                // the "new" method has been natively implemented; we should not attempt to generate
                // the constructor
                return;
            }
        }

        MethodModel mm = jmd.isOptimized
                ? findMethod(jitName+OPT, jmd.optimizedMD)
                : findMethod(jitName, jmd.standardMD);

        if (mm != null &&
                ((mm.flags().flagsMask() & ClassFile.ACC_ABSTRACT) == 0 ||
                    method.isAbstract() || method.isNative())) {
            // the method is already copied by the NativeTypeSystem
            return;
        }

        if (method.getHead().isNative()) {
            // throw new IllegalStateException(...);
            System.err.println("*** Native implementation is missing " + className + "#" + jitName +
                " for " + method.getSignature().getValueString());
            return;
        }

        super.assembleMethod(className, classBuilder, method, jitName, jmd);
    }

    @Override
    protected void assembleXvmType(String className, ClassBuilder classBuilder) {
        MethodModel mm = findMethod("$xvmType", MD_xvmType);
        if (mm == null) {
            super.assembleXvmType(className, classBuilder);
        }
    }

    @Override
    protected void assembleNew(String className, ClassBuilder classBuilder, MethodInfo constructor,
                               String jitName, JitMethodDesc jmd) {
        MethodModel mm = jmd.isOptimized
                ? findMethod(jitName+OPT, jmd.optimizedMD)
                : findMethod(jitName, jmd.standardMD);

        if (mm != null) {
            return;
        }
        super.assembleNew(className, classBuilder, constructor, jitName, jmd);
    }

    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Find a FieldModel for the specified property.
     */
    protected FieldModel findField(String jitName) {
        for (FieldModel fm : model.fields()) {
            if (fm.fieldName().equalsString(jitName)) {
                return fm;
            }
        }
        return null;
    }

    /**
     * Find a MethodModel for the specified method.
     */
    protected MethodModel findMethod(String jitName, MethodTypeDesc md) {
        for (MethodModel mm : model.methods()) {
            if (mm.methodName().equalsString(jitName) &&
                (md == null ||
                    mm.methodTypeSymbol().descriptorString().equals(md.descriptorString()))) {
                return mm;
            }
        }
        return null;
    }
}
