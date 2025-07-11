package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;

import java.lang.constant.ClassDesc;

import java.lang.constant.MethodTypeDesc;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.TypeSystem;

import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

/**
 * The builder for Int64 type.
 */
public class Int64Builder extends CommonBuilder {

    public Int64Builder(TypeSystem typeSystem, TypeConstant type) {
        super(typeSystem, type);
    }

    private static final ClassDesc CD_xInt64 =
        ClassDesc.of(org.xvm.javajit.intrinsic.numbers.xInt64.class.getName());

    @Override
    public void assembleImplClass(String className, ClassBuilder classBuilder) {
        classBuilder
            .withFlags(ClassFile.ACC_PUBLIC)
            .withSuperclass(CD_xInt64)
            ;

        // public [className](long value) {
        //    super(value);
        // }
        classBuilder.withMethod(INIT_NAME,
            MethodTypeDesc.of(CD_void, CD_long),
            ClassFile.ACC_PUBLIC,
            methodBuilder -> methodBuilder.withCode(codeBuilder ->
                codeBuilder
                    .aload(0)
                    .lload(1)
                    .invokespecial(CD_xInt64, INIT_NAME, MethodTypeDesc.of(CD_void, CD_long))
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
