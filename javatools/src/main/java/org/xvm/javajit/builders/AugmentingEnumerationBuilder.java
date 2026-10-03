package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;

import org.xvm.asm.constants.PropertyInfo;

import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystem.Artifact;

/**
 * The augmenting builder for the native Enumeration type.
 */
public class AugmentingEnumerationBuilder
        extends AugmentingBuilder
        implements EnumerationBuilderSupport {
    /**
     * Create an {@link AugmentingEnumerationBuilder}.
     *
     * @param typeSystem  the {@link TypeSystem}
     * @param art         the {@link Artifact}
     * @param model       the {@link ClassModel} to augment, if {@code null} an empty model will
     *                    be used
     */
    public AugmentingEnumerationBuilder(TypeSystem typeSystem, Artifact art, ClassModel model) {
        super(typeSystem, art, model);
        if (!EnumerationBuilderSupport.isBaseEnumeration(art.type())) {
            throw new IllegalArgumentException("Invalid enumeration type " + art.type()
                + ", only the base Enumeration class can be augmented");
        }
    }

    @Override
    protected void assembleMethods(ClassBuilder classBuilder) {
        // don't call super, we do not augment any methods on Enumeration
    }

    @Override
    protected void assembleProperties(ClassBuilder classBuilder) {
        // don't call super!
        assembleAbstractProperty(classBuilder, PROP_COUNT);
        assembleAbstractProperty(classBuilder, PROP_NAMES);
        assembleAbstractProperty(classBuilder, PROP_VALUES);
        assembleAbstractProperty(classBuilder, PROP_BY_NAME);
    }

    protected void assembleAbstractProperty(ClassBuilder classBuilder, String propName) {
        PropertyInfo   prop = typeInfo.findProperty(propName);
        String         name = prop.ensureGetterJitMethodName(typeSystem);
        JitMethodDesc  jmd  = prop.getGetterJitDesc(this);

        int flags = ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT;
        classBuilder.withMethod(name, jmd.standardMD, flags, code -> {});
        if (jmd.isOptimized) {
            classBuilder.withMethod(name + OPT, jmd.optimizedMD, flags, code -> {});
        }
    }
}
