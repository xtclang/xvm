package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystem.Artifact;

import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

/**
 * The builder for Module types.
 */
public class PackageBuilder
        extends CommonBuilder {

    public PackageBuilder(TypeSystem typeSystem, Artifact art) {
        super(typeSystem, art);
    }

    @Override
    public ClassDesc getSuperCD() {
        return CD_nPackage;
    }

    @Override
    public boolean assembleClass(ClassBuilder classBuilder) {
        classBuilder
            .withFlags(ClassFile.ACC_PUBLIC)
            .withSuperclass(CD_nPackage);
        return true;
    }

    @Override
    protected void callSuperInitializer(CodeBuilder code) {
        // super($ctx, type);
        code.aload(0)
            .aload(code.parameterSlot(0));
        loadTypeConstant(code, thisType);
        code.invokespecial(getSuperCD(), INIT_NAME,
                MethodTypeDesc.of(CD_void, CD_Ctx, CD_TypeConstant));
    }
}
