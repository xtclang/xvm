package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.FieldModel;
import java.lang.classfile.MethodModel;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.TypeSystem;

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
    protected ClassDesc getSuperDesc() {
        return model.superclass().get().asSymbol();
    }

    @Override
    public void assembleImplClass(String className, ClassBuilder classBuilder) {
        // AugmentingBuilder uses the native class attributes except of the "ABSTRACT" flag
        // that is driven by the type
        int flags = model.flags().flagsMask();

        if ((flags & ClassFile.ACC_ABSTRACT) != 0 && !typeInfo.isAbstract()) {
            classBuilder.withFlags(flags & ~ClassFile.ACC_ABSTRACT);
        }

        // implemented interfaces may not be native; add them if necessary
        assembleImplInterfaces(classBuilder);
    }

    @Override
    protected void assembleField(String className, ClassBuilder classBuilder, PropertyInfo prop) {
        String jitName = prop.getIdentity().ensureJitPropertyName(typeSystem);
        if (findField(jitName) == null) {
            super.assembleField(className, classBuilder, prop);
        }
    }

    @Override
    protected void assembleMethod(String className, ClassBuilder classBuilder, MethodInfo method,
                                  String jitName, MethodTypeDesc md, boolean optimized) {
        MethodModel mm = findMethod(jitName, md, optimized);
        if (mm != null) {
            if ((mm.flags().flagsMask() & ClassFile.ACC_ABSTRACT) == 0 ||
                    method.isAbstract() || method.isNative()) {
                // the method is already copied by the NativeTypeSystem
                return;
            }
            // the native method is marked as "abstract" and needs to be generated
        }

        if (method.getHead().isNative()) {
            // throw new IllegalStateException(...);
            System.err.println("*** Native implementation is missing " + className + "#" + jitName +
                " for " + method.getSignature().getValueString());
            return;
        }

        super.assembleMethod(className, classBuilder, method, jitName, md, optimized);
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
    protected MethodModel findMethod(String jitName, MethodTypeDesc md, boolean optimized) {
        for (MethodModel mm : model.methods()) {
            if (mm.methodName().equalsString(jitName) &&
                    mm.methodTypeSymbol().descriptorString().equals(md.descriptorString())) {
                return mm;
            }
        }
        return null;
    }
}
