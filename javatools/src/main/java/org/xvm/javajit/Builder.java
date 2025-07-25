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

    String Object     = "org.xtclang.ecstasy.Object";
    String Boolean    = "org.xtclang.ecstasy.Boolean";
    String xConst     = "org.xtclang.ecstasy.xConst";
    String xFunction  = "org.xtclang.ecstasy.xFunction";
    String xModule    = "org.xtclang.ecstasy.xModule";
    String xObj       = "org.xtclang.ecstasy.xObj";
    String xService   = "org.xtclang.ecstasy.xService";
    String xType      = "org.xtclang.ecstasy.xType";

    String String     = "org.xtclang.ecstasy.text.String";

    String Int64      = "org.xtclang.ecstasy.numbers.Int64";

    // ----- well-known class suffixes -------------------------------------------------------------

    String OPT = "$p"; // method contains primitive types

    // ----- well-known class descriptors ----------------------------------------------------------

    ClassDesc CD_xObj       = ClassDesc.of(xObj);
    ClassDesc CD_xConst     = ClassDesc.of(xConst);

    ClassDesc CD_Container  = ClassDesc.of(Container.class.getName());
    ClassDesc CD_Ctx        = ClassDesc.of(Ctx.class.getName());
    ClassDesc CD_TypeSystem = ClassDesc.of(TypeSystem.class.getName());
}
