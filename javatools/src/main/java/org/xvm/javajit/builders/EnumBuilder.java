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
    protected boolean assembleImplClass(String className, ClassBuilder classBuilder) {
        classBuilder.withSuperclass(getSuperCD())
                    .withFlags(ClassFile.ACC_PUBLIC | ClassFile.ACC_ABSTRACT);
        return true;
    }

    @Override
    protected ClassDesc getSuperCD() {
        return CD_nEnum;
    }

    @Override
    protected void assembleImplProperties(String className, ClassBuilder classBuilder) {
        assembleEnumerationProp(classBuilder);

        super.assembleImplProperties(className, classBuilder);
    }

    private void assembleEnumerationProp(ClassBuilder classBuilder) {
        PropertyInfo  prop       = typeInfo.findProperty("enumeration");
        String        getterName = prop.ensureGetterJitMethodName(typeSystem);
        JitMethodDesc jmDesc     = prop.getGetterJitDesc(this);
        TypeConstant  enumType   = thisId.getValueType(pool(), null);
        ClassDesc     cdEnum     = ensureClassDesc(enumType);

        classBuilder.withMethod(getterName, jmDesc.standardMD, ClassFile.ACC_PUBLIC,
            methodBuilder -> methodBuilder.withCode(code ->
                code.getstatic(cdEnum, Instance, cdEnum)
                    .areturn()));
    }

    @Override
    protected void assembleImplMethods(String className, ClassBuilder classBuilder) {
        // generate "equals" and "compare" functions
        SignatureConstant eqSig    = pool().sigEquals();
        MethodInfo        eqMethod = typeInfo.getMethodBySignature(eqSig);
        JitMethodDesc     eqJmd    = eqMethod.getJitDesc(this, typeInfo.getType());

        assert eqMethod != null;

        classBuilder.withMethod(eqSig.getName()+OPT, eqJmd.optimizedMD,
            ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
            methodBuilder -> methodBuilder.withCode(this::assembleEquals));

        SignatureConstant cmpSig    = pool().sigCompare();
        MethodInfo        cmpMethod = typeInfo.getMethodBySignature(cmpSig);
        JitMethodDesc     cmpJmd    = cmpMethod.getJitDesc(this, typeInfo.getType());

        assert cmpMethod != null;

        classBuilder.withMethod(cmpSig.getName(), cmpJmd.standardMD,
            ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC,
            methodBuilder -> methodBuilder.withCode(this::assembleCompare));

        super.assembleImplMethods(className, classBuilder);
    }

    /**
     * The signature of the function we generate is:
     *    "public boolean equals$p(Ctx ctx, nType CompileType, [EnumType] o1, {EnumType} o2)"
     */
    private void assembleEquals(CodeBuilder code) {
        // all we need is to call the equivalent function on xEnum
        code.aload(0)
            .aload(1)
            .aload(2)
            .aload(3)
            .invokestatic(CD_nEnum, "equals$p", MethodTypeDesc.of(CD_boolean,
                                                                  CD_Ctx, CD_nType, CD_nEnum,
                                                                  CD_nEnum))
            .ireturn();
    }

    /**
     * The signature of the function we generate is:
     *    "public Ordered compare(Ctx ctx, nType CompileType, [EnumType] o1, {EnumType} o2)"
     */
    private void assembleCompare(CodeBuilder code) {
        // there is a custom primitivized function on nEnum:
        //      long compare$p(Ctx ctx, nType CompileType, nEnum o1, nEnum o2)
        // which returns a negative, zero or positive value that needs to be translated into
        // the corresponding Ordered value

        ConstantPool pool = pool();

        // long c = nEnum.compare$p(ctx, CompileType, o1, o2);
        code.aload(0)
            .aload(1)
            .aload(2)
            .aload(3)
            .invokestatic(CD_nEnum, "compare$p", MethodTypeDesc.of(CD_long,
                                                                   CD_Ctx, CD_nType, CD_nEnum,
                                                                   CD_nEnum))
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
