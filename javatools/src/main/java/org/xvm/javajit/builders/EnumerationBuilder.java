package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;

import java.lang.classfile.ClassFile;
import java.lang.constant.ClassDesc;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystem.Artifact;

/**
 * The builder for Enumeration types.
 *
 * It overrides the CommonBuilder to do the following:
 *   - augment the Java constructor
 *   - add synthetic "$names" and "$values" properties
 *   - implement "count", "names" and "values" properties
 */
public class EnumerationBuilder extends CommonBuilder {
    public EnumerationBuilder(TypeSystem typeSystem, Artifact art) {
        TypeConstant type = art.type();
        ConstantPool pool = type.getConstantPool();
        if (type.isA(pool.typeEnumeration())) {
            enumType = art.type().getParamType(0);
        } else {
            // convert the Artifact for type T into the Artifact for type Enumeration<T>
            enumType = type;
            art = new Artifact(
                    pool.ensureParameterizedTypeConstant(pool.typeEnumeration(), type),
                    (ClassStructure) pool.clzEnumeration().getComponent(),
                    art.shape(),
                    art.className());
        }
        super(typeSystem, art);
    }

    public final TypeConstant enumType;

    @Override
    public ClassDesc getSuperCD() {
        return CD_Enumeration;
    }

    @Override
    protected void assembleProperties(ClassBuilder classBuilder) {
        assembleNamesProp(classBuilder);
        // TODO: assembleValuesProp()
        // don't call super!
    }

    private void assembleNamesProp(ClassBuilder classBuilder) {
        PropertyInfo  prop       = typeInfo.findProperty("names");
        String        getterName = prop.ensureGetterJitMethodName(typeSystem);
        JitMethodDesc jmDesc     = prop.getGetterJitDesc(this);
        ClassDesc     cdEnum     = ensureClassDesc(enumType);

        classBuilder.withMethodBody(getterName, jmDesc.standardMD, ClassFile.ACC_PUBLIC, code ->
            code.getstatic(cdEnum, NAMES, cdEnum)
                .areturn());
    }

    @Override
    protected void assembleMethods(ClassBuilder classBuilder) {
        // don't call super!
    }

    /**
     * The name of the property holding the enum names.
     */
    public static final String NAMES = "$names";

    /**
     * The name of the property holding the enum values.
     */
    public static final String VALUES = "$values";

}
