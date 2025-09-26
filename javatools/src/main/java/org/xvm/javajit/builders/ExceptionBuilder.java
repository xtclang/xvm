package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystem.ClassfileShape;

import static java.lang.constant.ConstantDescs.CD_Throwable;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

/**
 * The builder for Exception types.
 *
 * For every natural subtype "XYZ" of "ecstasy.Exception" we create a method that overrides
 * the "$createJavaException(Throwable cause)" method at "org.xtclang.ecstasy.Exception" class that
 * looks like the following:
 * <pre>
 *  {@code public xException $createJavaException(Throwable cause) {
 *      return new e$XYZ(cause, this);
 *  }}
 * </pre>
 *
 * Then, the class builder for "XYZ" will generate the corresponding "e$XYZ" class of
 * {@link TypeSystem.ClassfileShape#Exception Exception shape} via {@link #assembleJavaException} method.
 */
public class ExceptionBuilder extends CommonBuilder {
    public ExceptionBuilder(TypeSystem typeSystem, TypeConstant type) {
        super(typeSystem, type);
    }

    @Override
    protected void assembleImplMethods(String className, ClassBuilder classBuilder) {
        super.assembleImplMethods(className, classBuilder);

        assembleCreateException(className, classBuilder);
    }

    public void assembleCreateException(String className, ClassBuilder classBuilder) {
        String         jitName  = "$createJavaException";
        MethodTypeDesc createMD = MethodTypeDesc.of(CD_xException, CD_Throwable);
        ClassDesc      javaExCD = getShapeDesc(className, ClassfileShape.Exception);
        ClassDesc      thisCD   = ClassDesc.of(className);
        MethodTypeDesc initMD   = MethodTypeDesc.of(CD_void, CD_Throwable, thisCD);

        classBuilder.withMethod(jitName, createMD, ClassFile.ACC_PUBLIC,
            methodBuilder -> methodBuilder.withCode(code -> {
                code.new_(javaExCD)
                    .dup()
                    .aload(code.parameterSlot(0))
                    .aload(0)
                    .invokespecial(javaExCD, INIT_NAME, initMD)
                    .areturn();
            })
        );
    }
}
