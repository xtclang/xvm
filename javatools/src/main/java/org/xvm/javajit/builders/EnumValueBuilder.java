package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;

import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;

import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.TypeSystem;

/**
 * The builder for Enum value types.
 *
 * It overrides the CommonBuilder to do the following:
 *   - create a synthetic "$name" field to hold the name
 *   - supply the "ordinal" and "name" properties
 */
public class EnumValueBuilder extends CommonBuilder {

    public EnumValueBuilder(TypeSystem typeSystem, TypeConstant type) {
        super(typeSystem, type);
    }

    @Override
    protected boolean hasStaticInitializer() {
        return true;
    }

    @Override
    protected void augmentStaticInitializer(String className, CodeBuilder code) {
        super.augmentStaticInitializer(className, code);

        String name = classStruct.getName();

        code.aconst_null()
            .loadConstant(name)
            .invokestatic(CD_String, "of", MD_StringOf)
            .putstatic(ClassDesc.of(className), NAME, CD_String);
    }

    @Override
    protected void assembleImplProperties(String className, ClassBuilder classBuilder) {

        // public static final String $name;
        classBuilder.withField(NAME, CD_String,
            ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL);

        assembleOrdinalProp(classBuilder);
        assembleNameProp(className, classBuilder);

        super.assembleImplProperties(className, classBuilder);
    }

    private void assembleOrdinalProp(ClassBuilder classBuilder) {
        PropertyInfo  prop       = typeInfo.findProperty("ordinal");
        String        getterName = prop.getGetterId().ensureJitMethodName(typeSystem);
        JitMethodDesc jmDesc     = prop.getGetterJitDesc(typeSystem);

        ClassStructure enumStruct = (ClassStructure) classStruct.getParent();
        assert enumStruct.getFormat() == Component.Format.ENUM;

        int ord = -1;
        for (Component child : enumStruct.children()) {
            if (child.getFormat() == Component.Format.ENUMVALUE) {
                ord++;
                if (child == classStruct) {
                    break;
                }
            }
        }
        assert ord >= 0;

        final long ordinal = ord;
        classBuilder.withMethod(getterName+OPT, jmDesc.optimizedMD, ClassFile.ACC_PUBLIC,
            methodBuilder -> methodBuilder.withCode(code ->
                code.loadConstant(ordinal)
                    .lreturn()));
    }

    private void assembleNameProp(String className, ClassBuilder classBuilder) {
        PropertyInfo  prop       = typeInfo.findProperty("name");
        String        getterName = prop.getGetterId().ensureJitMethodName(typeSystem);
        JitMethodDesc jmDesc     = prop.getGetterJitDesc(typeSystem);

        classBuilder.withMethod(getterName, jmDesc.standardMD, ClassFile.ACC_PUBLIC,
            methodBuilder -> methodBuilder.withCode(code ->
                code.getstatic(ClassDesc.of(className), NAME, CD_String)
                    .areturn()));
    }

    /**
     * The name of the property holding the enum's name.
     */
    public static String NAME = "$name";
}
