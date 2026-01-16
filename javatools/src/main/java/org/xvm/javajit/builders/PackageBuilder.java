package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.TypeSystem;

import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

/**
 * The builder for Module types.
 */
public class PackageBuilder
        extends CommonBuilder {

    public PackageBuilder(TypeSystem typeSystem, TypeConstant type) {
        super(typeSystem, type);
    }

    @Override
    protected ClassDesc getSuperCD() {
        return CD_nPackage;
    }

    @Override
    public void assembleImplClass(String className, ClassBuilder classBuilder) {
        classBuilder
            .withFlags(ClassFile.ACC_PUBLIC)
            .withSuperclass(CD_nPackage)
            ;
    }

    @Override
    protected void callSuperInitializer(CodeBuilder code) {
        // super($ctx, type);
        code.aload(0)
            .aload(code.parameterSlot(0));
        loadTypeConstant(code, typeSystem, typeInfo.getType());
        code.invokespecial(getSuperCD(), INIT_NAME,
                MethodTypeDesc.of(CD_void, CD_Ctx, CD_TypeConstant));
    }
}
