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

    String N_Object    = "org.xtclang.ecstasy.Object";
    String N_Boolean   = "org.xtclang.ecstasy.Boolean";
    String N_Null      = "org.xtclang.ecstasy.Null";
    String N_xConst    = "org.xtclang.ecstasy.xConst";
    String N_xFunction = "org.xtclang.ecstasy.xFunction";
    String N_xModule   = "org.xtclang.ecstasy.xModule";
    String N_xObj      = "org.xtclang.ecstasy.xObj";
    String N_xService  = "org.xtclang.ecstasy.xService";
    String N_xType     = "org.xtclang.ecstasy.xType";

    String N_Char      = "org.xtclang.ecstasy.text.Char";
    String N_String    = "org.xtclang.ecstasy.text.String";

    String N_Int64     = "org.xtclang.ecstasy.numbers.Int64";

    // ----- well-known class suffixes -------------------------------------------------------------

    String OPT = "$p"; // method contains primitive types

    // ----- well-known class descriptors ----------------------------------------------------------

    ClassDesc CD_xObj       = ClassDesc.of(N_xObj);
    ClassDesc CD_xConst     = ClassDesc.of(N_xConst);

    ClassDesc CD_Boolean    = ClassDesc.of(N_Boolean);
    ClassDesc CD_Char       = ClassDesc.of(N_Char);
    ClassDesc CD_Int64      = ClassDesc.of(N_Int64);
    ClassDesc CD_Null       = ClassDesc.of(N_Null);
    ClassDesc CD_Object     = ClassDesc.of(N_Object);
    ClassDesc CD_String     = ClassDesc.of(N_String);

    ClassDesc CD_Container  = ClassDesc.of(Container.class.getName());
    ClassDesc CD_Ctx        = ClassDesc.of(Ctx.class.getName());
    ClassDesc CD_TypeSystem = ClassDesc.of(TypeSystem.class.getName());
}
