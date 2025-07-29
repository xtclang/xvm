package org.xvm.javajit;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.constants.TypeConstant;

import static java.lang.constant.ConstantDescs.CD_void;

/**
 * Java class builder.
 */
public interface Builder {
    /**
     * Assemble the java class for an "impl" shape.
     */
    default void assembleImpl(String className, ClassBuilder classBuilder) {
        throw new UnsupportedOperationException();
    }

    /**
     * Assemble the java class for a "pure" shape.
     */
    default void assemblePure(String className, ClassBuilder classBuilder) {
        throw new UnsupportedOperationException();
    }

    // ----- helper methods ------------------------------------------------------------------------

    /**
     * Compute a MethodTypeDesc for a Java method with the specified parameter and return types.
     */
    static MethodTypeDesc computeMethodDesc(TypeSystem typeSystem, TypeConstant[] paramTypes,
                                            TypeConstant[] returnTypes) {
        int         paramCount = paramTypes.length;
        ClassDesc[] paramCDs   = new ClassDesc[paramCount + 1];

        paramCDs[0] = ClassDesc.of(Ctx.class.getName());

        for (int i = 0; i < paramCount; i++)
            {
            paramCDs[i+1] = ClassDesc.of(paramTypes[i].ensureJitClassName(typeSystem));
            }
        return MethodTypeDesc.of(returnTypes.length == 0 ? CD_void :
                ClassDesc.of(returnTypes[0].ensureJitClassName(typeSystem)), paramCDs);
        }

    /**
     * Generate a default value "load" for the specified Java class.
     */
    static void defaultLoad(CodeBuilder codeBuilder, ClassDesc cd) {
        if (cd.isPrimitive()) {
            switch (cd.descriptorString()) {
                case "I", "S", "B", "C", "Z":
                    codeBuilder.iconst_0();
                    break;
                case "J":
                    codeBuilder.lconst_0();
                    break;
                case "F":
                    codeBuilder.fconst_0();
                    break;
                case "D":
                    codeBuilder.dconst_0();
                    break;
                case "V":
                    break;
            }
        } else {
            codeBuilder.aconst_null();
        }
    }

    /**
     * Generate a default return for the specified Java class.
     */
    static void defaultReturn(CodeBuilder codeBuilder, ClassDesc cd) {
        if (cd.isPrimitive()) {
            switch (cd.descriptorString()) {
                case "I", "S", "B", "C", "Z":
                    codeBuilder.ireturn();
                    break;
                case "J":
                    codeBuilder.lreturn();
                    break;
                case "F":
                    codeBuilder.freturn();
                    break;
                case "D":
                    codeBuilder.dreturn();
                    break;
                case "V":
                    codeBuilder.return_();
                    break;
            }
        } else {
            codeBuilder.aconst_null().areturn();
        }
    }

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
    ClassDesc CD_xFunction  = ClassDesc.of(N_xFunction);

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
