package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;

import java.lang.classfile.ClassFile;
import java.lang.classfile.CodeBuilder;
import java.lang.classfile.Label;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.PropertyInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.TypeSystem;

import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_long;

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

    @Override
    protected void assembleImplMethods(String className, ClassBuilder classBuilder) {
        // generate "equals" and "compare" functions
        SignatureConstant eqSig    = typeSystem.pool().sigEquals();
        MethodInfo        eqMethod = typeInfo.getMethodBySignature(eqSig);
        JitMethodDesc     eqJmd    = eqMethod.getJitDesc(typeSystem);

        assert eqMethod != null;

        classBuilder.withMethod(eqSig.getName()+OPT, eqJmd.optimizedMD,
            ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
            methodBuilder -> methodBuilder.withCode(this::assembleEquals));

        SignatureConstant cmpSig    = typeSystem.pool().sigCompare();
        MethodInfo        cmpMethod = typeInfo.getMethodBySignature(cmpSig);
        JitMethodDesc     cmpJmd    = cmpMethod.getJitDesc(typeSystem);

        assert cmpMethod != null;

        classBuilder.withMethod(cmpSig.getName(), cmpJmd.standardMD,
            ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
            methodBuilder -> methodBuilder.withCode(this::assembleCompare));

        super.assembleImplMethods(className, classBuilder);
    }

    /**
     * The signature of the function we generate is:
     *    "public boolean equals$p(Ctx ctx, xType CompileType, [EnumType] o1, {EnumType} o2)"
     */
    private void assembleEquals(CodeBuilder code) {
        // all we need is to call the equivalent function on xEnum
        code.aload(0)
            .aload(1)
            .aload(2)
            .aload(3)
            .invokestatic(CD_xEnum, "equals$p", MethodTypeDesc.of(CD_boolean,
                    CD_Ctx, CD_xType, CD_xEnum, CD_xEnum))
            .ireturn();
    }

    /**
     * The signature of the function we generate is:
     *    "public Ordered compare(Ctx ctx, xType CompileType, [EnumType] o1, {EnumType} o2)"
     */
    private void assembleCompare(CodeBuilder code) {
        // there is a custom primitivized function on xEnum:
        //      long compare$p(Ctx ctx, xType CompileType, xEnum o1, xEnum o2)
        // which returns a negative, zero or positive value that needs to be translated into
        // the corresponding Ordered value

        ConstantPool pool = typeSystem.pool();

        // long c = xEnum.compare$p(ctx, CompileType, o1, o2);
        code.aload(0)
            .aload(1)
            .aload(2)
            .aload(3)
            .invokestatic(CD_xEnum, "compare$p", MethodTypeDesc.of(CD_long,
                    CD_Ctx, CD_xType, CD_xEnum, CD_xEnum))
            .lstore(4);

        Label labelGe = code.newLabel();
        Label labelEq = code.newLabel();

        // if (l < 0)
        code.lload(4);
        code.lconst_0();
        code.lcmp();
        code.ifge(labelGe);

        // return Lesser;
        loadConstant(code, pool.valLesser());
        code.areturn();

        // else if (l > 0)
        code.labelBinding(labelGe);
        code.lload(4);
        code.lconst_0();
        code.lcmp();
        code.ifeq(labelEq);             // if <= 0, jump to else

        // return Greater;
        loadConstant(code, pool.valGreater());
        code.areturn();

        // else return Equal;
        code.labelBinding(labelEq);
        loadConstant(code, pool.valEqual());
        code.areturn();
    }
}
