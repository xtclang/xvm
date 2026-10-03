package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.IdentityConstant;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.TypeSystem;
import org.xvm.javajit.TypeSystem.Artifact;

import static java.lang.constant.ConstantDescs.CD_MethodHandle;
import static java.lang.constant.ConstantDescs.CD_long;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

/**
 * The builder for Enumeration types.
 *
 * <p>It overrides the CommonBuilder to do the following:
 *   - augment the Java constructor
 *   - add synthetic "$Instance", "$names" and "$values" properties
 *   - implement "count", "names", "values" and "byValues" properties
 */
public class EnumerationBuilder
        extends CommonBuilder
        implements  EnumerationBuilderSupport {
    /**
     * Create an {@link EnumerationBuilder}.
     *
     * @param typeSystem the {@link TypeSystem}
     * @param art        the {@link Artifact} to build
     */
    public EnumerationBuilder(TypeSystem typeSystem, Artifact art) {
        TypeConstant     type = art.type();
        ConstantPool     pool = type.getConstantPool();
        IdentityConstant id   = type.getSingleUnderlyingClass(true);

        // convert the Artifact for type T into the Artifact for type Enumeration<T>
        art = new Artifact(
                pool.ensureParameterizedTypeConstant(pool.typeEnumeration(), type),
                (ClassStructure) pool.clzEnumeration().getComponent(),
                art.shape(),
                art.className());

        enumValues = EnumerationBuilderSupport.getEnumValues((ClassStructure) id.getComponent());
        enumType   = type;

        super(typeSystem, art);
    }

    /**
     * The type of the enum being built.
     */
    public final TypeConstant enumType;

    /**
     * The values of the enum being built.
     */
    protected final ClassStructure[] enumValues;

    @Override
    public boolean assembleClass(ClassBuilder classBuilder) {
        classBuilder.withSuperclass(CD_Enumeration)
                    .withFlags(ClassFile.ACC_PUBLIC);
        return true;
    }

    @Override
    protected void assembleProperties(ClassBuilder classBuilder) {
        // don't call super!
        assembleCountProp(classBuilder);
        assembleNamesProp(classBuilder);
        assembleValuesProp(classBuilder);
        assembleByNameProp(classBuilder);

        ClassDesc cd    = isContainerScoped(thisType) ? CD_MethodHandle : art.CD();
        int       flags = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL;

        // public static final $INSTANCE;
        classBuilder.withField(Instance, cd, flags);

        // public static final ArrayᐸObjectᐳ $names;
        PropertyInfo  nameProp = typeInfo.findProperty(PROP_NAMES);
        JitMethodDesc nameJMD  = nameProp.getGetterJitDesc(this);
        classBuilder.withField(FIELD_NAMES, nameJMD.standardMD.returnType(), flags);

        // public static final ArrayᐸEnumTypeᐳ $values;
        PropertyInfo  valuesProp = typeInfo.findProperty(PROP_VALUES);
        JitMethodDesc valuesJMD  = valuesProp.getGetterJitDesc(this);
        classBuilder.withField(FIELD_VALUES, valuesJMD.standardMD.returnType(), flags);
    }

    private void assembleCountProp(ClassBuilder classBuilder) {
        PropertyInfo  prop       = typeInfo.findProperty(PROP_COUNT);
        String        getterName = prop.ensureGetterJitMethodName(typeSystem);
        JitMethodDesc jmDesc     = prop.getGetterJitDesc(this);
        String        jitName    = getterName + OPT;

        classBuilder.withMethodBody(jitName, jmDesc.optimizedMD, ClassFile.ACC_PUBLIC,
            code -> {
                code.loadConstant((long) enumValues.length)
                    .lreturn();
            });

        classBuilder.withMethodBody(getterName, jmDesc.standardMD, ClassFile.ACC_PUBLIC,
            code -> {
                code.loadConstant((long) enumValues.length)
                    .invokestatic(CD_Int64, "$box", md(CD_Int64, CD_long))
                    .areturn();
            });
    }

    private void assembleNamesProp(ClassBuilder classBuilder) {
        PropertyInfo   prop       = typeInfo.findProperty(PROP_NAMES);
        String         getterName = prop.ensureGetterJitMethodName(typeSystem);
        JitMethodDesc  jmDesc     = prop.getGetterJitDesc(this);
        MethodTypeDesc md         = jmDesc.standardMD;

        classBuilder.withMethodBody(getterName, md, ClassFile.ACC_PUBLIC, code ->
                code.getstatic(art.CD(), FIELD_NAMES, md.returnType())
                    .areturn());
    }

    private void assembleValuesProp(ClassBuilder classBuilder) {
        PropertyInfo   prop       = typeInfo.findProperty(PROP_VALUES);
        String         getterName = prop.ensureGetterJitMethodName(typeSystem);
        JitMethodDesc  jmDesc     = prop.getGetterJitDesc(this);
        MethodTypeDesc md         = jmDesc.standardMD;

        classBuilder.withMethodBody(getterName, md, ClassFile.ACC_PUBLIC, code ->
        code.getstatic(art.CD(), FIELD_VALUES, md.returnType())
            .areturn());
    }

    private void assembleByNameProp(ClassBuilder classBuilder) {
        PropertyInfo   prop       = typeInfo.findProperty(PROP_BY_NAME);
        String         getterName = prop.ensureGetterJitMethodName(typeSystem);
        JitMethodDesc  jmDesc     = prop.getGetterJitDesc(this);
        MethodTypeDesc md         = jmDesc.standardMD;

        classBuilder.withMethodBody(getterName, md, ClassFile.ACC_PUBLIC, code -> {
            int ctxSlot = code.parameterSlot(0);
            ArrayBuilder.throwIllegalState(code, "TODO not implemented yet", ctxSlot);
        });
    }

    @Override
    protected void appendCLInit(CodeBuilder code, int ctxSlot) {
        super.appendCLInit(code, ctxSlot);

        ClassDesc thisCD = art.CD();
        int       count  = enumValues.length;

        // set the $INSTANCE static field
        code.new_(thisCD)
            .dup()
            .aload(ctxSlot)
            .invokespecial(thisCD, INIT_NAME, MD_xvmVoid)
            .putstatic(thisCD, Instance, thisCD);

        // set the $names array static field
        code.aload(ctxSlot)
            .loadConstant(count)
            .anewarray(CD_String);
        for (int i = 0; i < count; i++) {
            ClassStructure value = enumValues[i];
            code.dup()
                .loadConstant(i)
                .aload(ctxSlot)
                .loadConstant(value.getName())
                .invokestatic(CD_String, "of", md(CD_String, CD_Ctx, CD_JavaString))
                .aastore();
        }
        MethodTypeDesc mdBoxString = md(CD_ArrayObj, CD_Ctx, CD_String.arrayType());
        code.invokestatic(CD_ArrayObj, "$makeStringArray", mdBoxString)
            .putstatic(thisCD, FIELD_NAMES, CD_ArrayObj);

        // set the $values array static field
        String    jitName = enumType.ensureJitClassName(typeSystem);
        ClassDesc paramCD = enumType.isJitPrimitive() ? ensureClassDesc(enumType) : CD_Object;
        code.aload(ctxSlot)
            .loadConstant(count)
            .anewarray(paramCD);
        for (int i = 0; i < count; i++) {
            ClassStructure value   = enumValues[i];
            ClassDesc      cdValue = ClassDesc.of(jitName + "$" + value.getName());
            code.dup()
                .loadConstant(i)
                .getstatic(cdValue, Instance, cdValue)
                .aastore();
        }
        PropertyInfo   propValues = typeInfo.findProperty(PROP_VALUES);
        JitMethodDesc  jmdValues  = propValues.getGetterJitDesc(this);
        ClassDesc      arrayCD    = jmdValues.standardMD.returnType();
        MethodTypeDesc mdBoxObj   = md(arrayCD, CD_Ctx, paramCD.arrayType());

        code.invokestatic(arrayCD, "$makeArray", mdBoxObj)
            .putstatic(thisCD, FIELD_VALUES, arrayCD);
    }

    @Override
    protected void assembleMethods(ClassBuilder classBuilder) {
        // don't call super!
        MethodTypeDesc mdSuper = md(CD_void, CD_Ctx, CD_TypeConstant);
        int            flags   = ClassFile.ACC_PUBLIC;

        classBuilder.withMethodBody(INIT_NAME, MD_xvmVoid, flags, code -> {
            code.aload(0)
                .aload(code.parameterSlot(0))
                .getstatic(art.CD(), "$sc0", CD_TypeConstant)
                .invokespecial(CD_Enumeration, INIT_NAME, mdSuper)
                .return_();
        });
    }
}
