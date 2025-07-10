package org.xvm.javajit;

import java.lang.classfile.ClassBuilder;

import java.lang.constant.ClassDesc;

/**
 * Java class builder.
 */
public interface Builder {
    /**
     * Assemble the java class for an "impl" shape.
     */
    void assembleImpl(String className, ClassBuilder builder);

    /**
     * Assemble the java class for a "pure" shape.
     */
    void assemblePure(String className, ClassBuilder builder);

    // ----- well-known class descriptors

    ClassDesc CD_Ctx        = ClassDesc.of(Ctx.class.getName());

    ClassDesc CD_xObj       = ClassDesc.of(org.xvm.javajit.intrinsic.xObj.class.getName());

    ClassDesc CD_TypeSystem = ClassDesc.of(TypeSystem.class.getName());

    ClassDesc CD_Container  = ClassDesc.of(Container.class.getName());
}
