package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;

import java.lang.constant.ClassDesc;

import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.TypeSystem;

/**
 * The builder for Enumeration types.
 *
 * It overrides the CommonBuilder to do the following:
 *   - augment the Java constructor
 *   - add synthetic "$names" and "$values" properties
 *   - implement "count", "names" and "values" properties
 */
public class EnumerationBuilder extends CommonBuilder {
    public EnumerationBuilder(TypeSystem typeSystem, TypeConstant type) {
        super(typeSystem, type);
    }

    @Override
    protected ClassDesc getSuperDesc() {
        return CD_Enumeration;
    }

    @Override
    protected void assembleImplProperties(String className, ClassBuilder classBuilder) {
        assembleNamesProp(classBuilder);

        // don't call super!
    }

    private void assembleNamesProp(ClassBuilder classBuilder) {
        PropertyInfo  prop       = typeInfo.findProperty("names");
        String        getterName = prop.getGetterId().ensureJitMethodName(typeSystem);
        JitMethodDesc jmDesc     = prop.getGetterJitDesc(typeSystem);
        TypeConstant  enumType   = typeInfo.getType().getParamType(0);
        ClassDesc     cdEnum     = enumType.ensureClassDesc(typeSystem);

//        classBuilder.withMethod(getterName, jmDesc.standardMD, ClassFile.ACC_PUBLIC,
//            methodBuilder -> methodBuilder.withCode(code ->
//                code.getstatic(cdEnum, NAMES, cdEnum)
//                    .areturn()));
    }

    @Override
    protected void assembleImplMethods(String className, ClassBuilder classBuilder) {
        // don't call super!
    }

    /**
     * The name of the property holding the enum names.
     */
    public static String NAMES = "$names";

    /**
     * The name of the property holding the enum values.
     */
    public static String VALUES = "$values";

}
