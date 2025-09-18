package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;

import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.TypeSystem;

/**
 * The builder for Enum base types.
 *
 * It overrides the CommonBuilder to do the following:
 *   - supply the xEnum class as a super class
 *   - implement the "enumeration" property
 */
public class EnumBuilder extends CommonBuilder {
    public EnumBuilder(TypeSystem typeSystem, TypeConstant type) {
        super(typeSystem, type);
    }

    @Override
    protected void assembleImplClass(String className, ClassBuilder classBuilder) {
        classBuilder.withSuperclass(getSuperDesc())
                    .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT);
    }

    @Override
    protected ClassDesc getSuperDesc() {
        return CD_xEnum;
    }

    @Override
    protected void assembleImplProperties(String className, ClassBuilder classBuilder) {
        assembleEnumerationProp(classBuilder);

        super.assembleImplProperties(className, classBuilder);
    }

    private void assembleEnumerationProp(ClassBuilder classBuilder) {
        PropertyInfo  prop       = typeInfo.findProperty("enumeration");
        String        getterName = prop.getGetterId().ensureJitMethodName(typeSystem);
        JitMethodDesc jmDesc     = prop.getGetterJitDesc(typeSystem);
        TypeConstant  enumType   = thisId.getValueType(typeSystem.pool(), null);
        ClassDesc     cdEnum     = enumType.ensureClassDesc(typeSystem);

        classBuilder.withMethod(getterName, jmDesc.standardMD, ClassFile.ACC_PUBLIC,
            methodBuilder -> methodBuilder.withCode(code ->
                code.getstatic(cdEnum, Instance, cdEnum)
                    .areturn()));
    }
}
