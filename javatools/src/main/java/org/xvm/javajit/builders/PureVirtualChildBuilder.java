package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;

import java.util.Set;

import org.xvm.asm.ClassStructure;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystem.Artifact;

import static java.lang.constant.ConstantDescs.CD_void;

/**
 * Augments a pure interface with virtual-child inheritance and raw backing-field access contracts.
 */
public class PureVirtualChildBuilder extends PureBuilder {
    public PureVirtualChildBuilder(TypeSystem typeSystem, Artifact art) {
        super(typeSystem, art);

        assert thisType.isVirtualChild();
    }

    @Override
    protected void addSuperInterfaces(Set<ClassDesc> interfaces) {
        // a phantom has no local declaration, so its underlying identity also counts as inherited
        boolean isPhantom = thisType.isPhantom();
        for (IdentityConstant id : typeInfo.getClassChain().keySet()) {
            if ((isPhantom || !id.equals(thisId)) &&
                    id.getComponent() instanceof ClassStructure clz && clz.isVirtualChildClass()) {
                TypeConstant type = id.getFormalType().resolveGenerics(pool(), thisType);
                interfaces.add(getPureImplDesc(type));
            }
        }
    }

    @Override
    protected void assembleProperties(ClassBuilder classBuilder) {
        super.assembleProperties(classBuilder);

        VirtualChildBuilder.forEachRawField(this, (name, cd) -> {
            int flags = ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT;
            classBuilder.withMethod(FieldGet + name, md(cd), flags, _ -> {});
            classBuilder.withMethod(FieldSet + name, md(CD_void, cd), flags, _ -> {});
        });
    }
}
