package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.ClassStructure;
import org.xvm.asm.Component;
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

        IdentityConstant id  = type.getSingleUnderlyingClass(false);
        ClassStructure   clz = (ClassStructure) id.getComponent();
        enumValues = clz.children()
                .stream()
                .filter(c -> c.getFormat() == Component.Format.ENUMVALUE)
                .map(ClassStructure.class::cast)
                .toArray(ClassStructure[]::new);

        super(typeSystem, art);
    }

    public final TypeConstant enumType;

    protected final ClassStructure[] enumValues;

    @Override
    public ClassDesc getSuperCD() {
        return CD_Enumeration;
    }

    @Override
    protected void assembleProperties(ClassBuilder classBuilder) {
        // don't call super!
        assembleCountProp(classBuilder);
        assembleNamesProp(classBuilder);
        assembleValuesProp(classBuilder);

        // public static final $INSTANCE;
        ClassDesc cd    = isContainerScoped(thisType) ? CD_MethodHandle : art.CD();
        int       flags = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC | ClassFile.ACC_FINAL;
        classBuilder.withField(Instance, cd, flags);
        // public static final ArrayᐸObjectᐳ $names;
        classBuilder.withField(NAMES, CD_ArrayObj, flags);
        // public static final ArrayᐸObjectᐳ $values;
        classBuilder.withField(VALUES, CD_ArrayObj, flags);
    }

    private void assembleCountProp(ClassBuilder classBuilder) {
        PropertyInfo  prop       = typeInfo.findProperty("count");
        String        getterName = prop.ensureGetterJitMethodName(typeSystem);
        JitMethodDesc jmDesc     = prop.getGetterJitDesc(this);

        classBuilder.withMethodBody(getterName + OPT, jmDesc.optimizedMD, ClassFile.ACC_PUBLIC,
                code -> {
            code.loadConstant((long) enumValues.length)
                .lreturn();
        });

        classBuilder.withMethodBody(getterName, jmDesc.standardMD, ClassFile.ACC_PUBLIC,
                code -> {
            code.loadConstant((long) enumValues.length)
                .invokestatic(CD_Int64, "$box", MethodTypeDesc.of(CD_Int64, CD_long))
                .areturn();
        });
    }

    private void assembleNamesProp(ClassBuilder classBuilder) {
        PropertyInfo  prop       = typeInfo.findProperty("names");
        String        getterName = prop.ensureGetterJitMethodName(typeSystem);
        JitMethodDesc jmDesc     = prop.getGetterJitDesc(this);

        classBuilder.withMethodBody(getterName, jmDesc.standardMD, ClassFile.ACC_PUBLIC, code ->
            code.getstatic(art.CD(), NAMES, CD_ArrayObj)
                .areturn());
    }

    private void assembleValuesProp(ClassBuilder classBuilder) {
        PropertyInfo  prop       = typeInfo.findProperty("values");
        String        getterName = prop.ensureGetterJitMethodName(typeSystem);
        JitMethodDesc jmDesc     = prop.getGetterJitDesc(this);

        classBuilder.withMethodBody(getterName, jmDesc.standardMD, ClassFile.ACC_PUBLIC, code ->
            code.getstatic(art.CD(), VALUES, CD_ArrayObj)
                .areturn());
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
            .invokespecial(thisCD, "<init>", MethodTypeDesc.of(CD_void, CD_Ctx))
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
                .invokestatic(CD_String, "of", MethodTypeDesc.of(CD_String, CD_Ctx, CD_JavaString))
                .aastore();
        }
        MethodTypeDesc mdBoxString = MethodTypeDesc.of(CD_ArrayObj, CD_Ctx, CD_String.arrayType());
        code.invokestatic(CD_ArrayObj, "$makeStringArray", mdBoxString)
            .putstatic(thisCD, NAMES, CD_ArrayObj);

        // set the $values array static field
        String jitName = enumType.ensureJitClassName(typeSystem);
        code.aload(ctxSlot)
            .loadConstant(count)
            .anewarray(CD_Object);
        for (int i = 0; i < count; i++) {
            ClassStructure value   = enumValues[i];
            ClassDesc      cdValue = ClassDesc.of(jitName + "$" + value.getName());
            code.dup()
                .loadConstant(i)
                .getstatic(cdValue, Instance, cdValue)
                .aastore();
        }
        MethodTypeDesc mdBoxObj = MethodTypeDesc.of(CD_ArrayObj, CD_Ctx, CD_Object.arrayType());
        code.invokestatic(CD_ArrayObj, "$makeArray", mdBoxObj)
            .putstatic(thisCD, VALUES, CD_ArrayObj);
    }

    @Override
    protected void assembleMethods(ClassBuilder classBuilder) {
        // don't call super!
        assembleConstructor(classBuilder);
    }

    private void assembleConstructor(ClassBuilder classBuilder) {
        MethodTypeDesc md      = MethodTypeDesc.of(CD_void, CD_Ctx);
        MethodTypeDesc mdSuper = MethodTypeDesc.of(CD_void, CD_Ctx, CD_TypeConstant);
        int            flags   = ClassFile.ACC_PUBLIC;

        classBuilder.withMethodBody("<init>", md, flags, code -> {
            code.aload(0)
                .aload(code.parameterSlot(0))
                .getstatic(art.CD(), "$sc0", CD_TypeConstant)
                .invokespecial(CD_Enumeration, "<init>", mdSuper)
                .return_();
        });
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
