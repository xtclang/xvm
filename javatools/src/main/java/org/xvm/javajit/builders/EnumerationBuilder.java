package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;

import java.lang.classfile.ClassFile;
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
    protected ClassDesc getSuperCD() {
        return CD_Enumeration;
    }

    @Override
    protected void assembleImplProperties(String className, ClassBuilder classBuilder) {
        assembleNamesProp(classBuilder);
        // TODO: assembleValuesProp()
        // don't call super!
    }

    private void assembleNamesProp(ClassBuilder classBuilder) {
        PropertyInfo  prop       = typeInfo.findProperty("names");
        String        getterName = prop.ensureGetterJitMethodName(typeSystem);
        JitMethodDesc jmDesc     = prop.getGetterJitDesc(this);
        TypeConstant  enumType   = typeInfo.getType().getParamType(0);
        ClassDesc     cdEnum     = ensureClassDesc(enumType);

        classBuilder.withMethodBody(getterName, jmDesc.standardMD, ClassFile.ACC_PUBLIC, code ->
            code.getstatic(cdEnum, NAMES, cdEnum)
                .areturn());
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
