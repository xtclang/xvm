package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystem.ClassfileShape;

import static java.lang.constant.ConstantDescs.CD_Throwable;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

/**
 * The builder for Exception types.
 * <p/>
 * For every natural subtype "XYZ" of "ecstasy.Exception" this builder creates a method that
 * overrides the {@code $createJavaException(Throwable cause)} method at
 * "org.xtclang.ecstasy.Exception" class.
 * <br>
 * In addition, this builder generates the "e$XYZ" class of
 * {@link ClassfileShape#Exception Exception shape} via {@link #assembleJavaException} method.
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

    /**
     * Add "$createJavaException" method to the exception class that looks like following:
     * <pre>
     *  {@code public xException $createJavaException(Throwable cause) {
     *      return new e$XYZ(cause, this);
     *  }}
     * </pre>
     */
    public void assembleCreateException(String className, ClassBuilder classBuilder) {
        String         jitName  = "$createJavaException";
        MethodTypeDesc createMD = MethodTypeDesc.of(CD_xException, CD_Throwable);
        ClassDesc      javaExCD = getShapeDesc(className, ClassfileShape.Exception);
        ClassDesc      thisCD   = ClassDesc.of(className);
        MethodTypeDesc initMD   = MethodTypeDesc.of(CD_void, CD_Throwable, thisCD);

        classBuilder.withMethod(jitName, createMD, ClassFile.ACC_PUBLIC,
            methodBuilder -> methodBuilder.withCode(code ->
                code.new_(javaExCD)
                    .dup()
                    .aload(code.parameterSlot(0))
                    .aload(0)
                    .invokespecial(javaExCD, INIT_NAME, initMD)
                    .areturn()
            )
        );
    }

    /**
     * The class for e$XYZ (this class name) should look like this:
     * <code><pre>
     * public class e$XYZ extends xException {
     *     public e$XYZ(Throwable cause, XYZ exception) {
     *         super(cause, exception);
     *     }
     *</pre></code>
     */
    public void assembleJavaException(String className, ClassBuilder classBuilder) {
        ConstantPool pool      = typeSystem.pool();
        TypeConstant superType = typeInfo.getExtends();
        ClassDesc    superCD;
        if (superType.equals(pool.typeException())) {
            superCD = CD_xException;
        } else {
            superCD = getShapeDesc(typeSystem.ensureJitClassName(superType), ClassfileShape.Exception);
        }

        ClassDesc exCD = ClassDesc.of(typeSystem.ensureJitClassName(typeInfo.getType()));

        classBuilder.withFlags(ClassFile.ACC_PUBLIC)
                    .withSuperclass(superCD);
        classBuilder.withMethod(INIT_NAME,
            MethodTypeDesc.of(CD_void, CD_Throwable, exCD),
            ClassFile.ACC_PUBLIC,
            methodBuilder -> methodBuilder.withCode(code -> {
                MethodTypeDesc superMD =  MethodTypeDesc.of(CD_void,
                        CD_Throwable, ClassDesc.of(superType.ensureJitClassName(typeSystem)));
                code.aload(0)
                    .aload(1)
                    .aload(2)
                    .checkcast(CD_Exception)
                    .invokespecial(superCD, INIT_NAME, superMD)
                    .return_();
            })
        );
    }
}
