package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.attribute.SourceFileAttribute;
import java.lang.constant.ClassDesc;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyConstant;
import org.xvm.asm.constants.PropertyInfo;

import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystem.Artifact;
import org.xvm.javajit.TypeSystem.ClassfileShape;

/**
 * Creates the Pure shape of an Ecstasy class: a Java interface declaring its virtual instance
 * methods and property accessors without their implementations or backing fields. Subclasses can
 * augment the interface hierarchy and instance contracts required by a particular representation.
 */
public class PureBuilder extends CommonBuilder {
    public PureBuilder(TypeSystem typeSystem, Artifact art) {
        assert art.shape() == ClassfileShape.Pure || isEmbedded(art.clz().getFormat());
        super(typeSystem, art);
    }

    @Override
    public void build(ClassBuilder classBuilder) {
        classBuilder.with(SourceFileAttribute.of(classStruct.getSourceFileName()));
        classBuilder.withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_INTERFACE | ClassFile.ACC_ABSTRACT);

        assembleInterfaces(classBuilder);
        assembleMethods(classBuilder);
        assembleProperties(classBuilder);
    }

    @Override
    protected void assembleInterfaces(ClassBuilder classBuilder) {
        List<ClassDesc> interfaces = new ArrayList<>();

        addContributionInterfaces(typeInfo, interfaces, true);

        interfaces.remove(art.CD());
        if (interfaces.isEmpty()) {
            interfaces.add(CD_Object);
        }

        classBuilder.withInterfaceSymbols(interfaces);
    }

    @Override
    protected void assembleMethods(ClassBuilder classBuilder) {
        for (Map.Entry<MethodConstant, MethodInfo> entry : typeInfo.getMethods().entrySet()) {
            // property methods (not at the top level) are assembled at the implementing class
            if (entry.getKey().isTopLevel()) {
                assembleMethod(classBuilder, entry.getValue());
            }
        }
    }

    @Override
    protected void assembleMethod(ClassBuilder classBuilder, MethodInfo method) {
        if (!method.isCtorOrValidator() && !method.isFunction() &&
                method.isVirtual() && !method.isCapped()) {
            addAbstractMethod(classBuilder, method.ensureJitMethodName(typeSystem),
                    method.getJitDesc(this));
        }
    }

    @Override
    protected void assembleProperties(ClassBuilder classBuilder) {
        for (Map.Entry<PropertyConstant, PropertyInfo> entry : typeInfo.getProperties().entrySet()) {
            PropertyInfo prop = entry.getValue();
            if (entry.getKey().isTopLevel() && !prop.isConstant() && !prop.isFormalType()) {
                addAbstractMethod(classBuilder, prop.ensureGetterJitMethodName(typeSystem),
                        prop.getGetterJitDesc(this, thisType));
                if (prop.isVar()) {
                    addAbstractMethod(classBuilder, prop.ensureSetterJitMethodName(typeSystem),
                            prop.getSetterJitDesc(this, thisType));
                }
            }
        }
    }
}
