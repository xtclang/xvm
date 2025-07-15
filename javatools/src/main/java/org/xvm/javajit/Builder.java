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

    // ----- native class names --------------------------------------------------------------------

    String xObj       = "org.xvm.javajit.intrinsic.xObj";
    String xBool      = "org.xvm.javajit.intrinsic.xBool";
    String xConst     = "org.xvm.javajit.intrinsic.xConst";
    String xContainer = "org.xvm.javajit.intrinsic.xContainer";
    String xModule    = "org.xvm.javajit.intrinsic.xModule";
    String xStr       = "org.xvm.javajit.intrinsic.xStr";
    String xInt64     = "org.xvm.javajit.intrinsic.numbers.xInt64";

    // ----- well-known class descriptors ----------------------------------------------------------

    ClassDesc CD_xObj       = ClassDesc.of(xObj);
    ClassDesc CD_xConst     = ClassDesc.of(xConst);
    ClassDesc CD_xContainer = ClassDesc.of(xContainer);

    ClassDesc CD_Container  = ClassDesc.of(Container.class.getName());
    ClassDesc CD_Ctx        = ClassDesc.of(Ctx.class.getName());
    ClassDesc CD_TypeSystem = ClassDesc.of(TypeSystem.class.getName());
}
