package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.TypeSystem;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

/**
 * The builder for Boolean type.
 */
public class BoolBuilder extends CommonBuilder {

    public BoolBuilder(TypeSystem typeSystem, TypeConstant type) {
        super(typeSystem, type);
    }

    private static final ClassDesc CD_xBool =
        ClassDesc.of(org.xvm.javajit.intrinsic.xBool.class.getName());

    @Override
    public void assembleImplClass(String className, ClassBuilder classBuilder) {
        classBuilder
            .withFlags(ClassFile.ACC_PUBLIC)
            .withSuperclass(CD_xBool)
            ;

        // public [className](boolean value) {
        //    super(value);
        // }
        classBuilder.withMethod(INIT_NAME,
            MethodTypeDesc.of(CD_void, CD_boolean),
            ClassFile.ACC_PUBLIC,
            methodBuilder -> methodBuilder.withCode(codeBuilder ->
                codeBuilder
                    .aload(0)
                    .iload(1)
                    .invokespecial(CD_xBool, INIT_NAME, MethodTypeDesc.of(CD_void, CD_boolean))
                    .return_()
            )
        );
    }

    @Override
    protected void assembleImplMethods(String className, ClassBuilder classBuilder) {
        // super.assembleImplMethods(className, classBuilder);
        // TODO
    }

    @Override
    public void assemblePure(String className, ClassBuilder builder) {
    }
}
