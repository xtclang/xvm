package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassFile;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import java.math.BigInteger;

import java.util.function.BiConsumer;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.TypeSystem;

import static java.lang.constant.ConstantDescs.CD_boolean;

/**
 * The builder for IntN and UIntN types.
 */
public class IntNBuilder extends IntNumberBuilder {

    public IntNBuilder(TypeSystem typeSystem, TypeSystem.Artifact art, ClassModel model) {
        super(typeSystem, art, model);
    }

    @Override
    protected void assembleMethod(ClassBuilder classBuilder, MethodInfo method,
                                  String jitName, JitMethodDesc jmd) {
        if (method.getHead().getMethodStructure().isPropertyInitializer()) {
            super.assembleMethod(classBuilder, method, jitName, jmd);
            return;
        }

        String         methodName = jmd.isOptimized ? jitName+OPT : jitName;
        MethodTypeDesc md         = jmd.isOptimized ? jmd.optimizedMD : jmd.standardMD;
        if (isNativeMethod(methodName, md)) {
            super.assembleMethod(classBuilder, method, jitName, jmd);
            return;
        }

        if (useNaturalImplementation(method)) {
            super.assembleMethod(classBuilder, method, jitName, jmd);
            return;
        }

        BiConsumer<CodeBuilder, JitMethodDesc> generator = jmd.isPrimitivized()
                ? getMethodCodeGenerator(method.getJitIdentity().getName())
                : null;
        if (generator == null) {
            generator = (code, jmd_) -> generateUnsupported(code, jmd_, jitName);
        }
        assembleGeneratedMethod(classBuilder, method, jitName, jmd, generator);
    }

    @Override
    protected void assembleNew(ClassBuilder classBuilder, MethodInfo constructor,
                               String jitName, JitMethodDesc jmd) {

        TypeConstant[] paramTypes = constructor.getSignature().getRawParams();
        if (paramTypes.length != 1) {
            // all other constructors require native new functions
            return;
        }

        ConstantPool pool        = pool();
        ClassDesc    thisCD      = art.CD();
        String       thisName    =  thisType.getSingleUnderlyingClass(false).getName();
        TypeConstant paramType   = paramTypes[0];
        int          flags       = ClassFile.ACC_PUBLIC | ClassFile.ACC_STATIC;
        ClassDesc    bigIntCD    = ClassDesc.of(BigInteger.class.getName());
        boolean      isBitArray  = paramType.isA(pool.typeBitArray());
        boolean      isByteArray = paramType.isA(pool.typeByteArray());
        boolean      isString    = paramType.isA(pool.typeString());


        if (isString) {
            classBuilder.withMethodBody(jitName, jmd.standardMD, flags, code -> {
                int    ctxSlot    = code.parameterSlot(jmd.optimizedCtx());
                int    stringSlot = code.parameterSlot(jmd.getImplicitParamCount());

                code.aload(ctxSlot)
                    .aload(stringSlot)
                    .invokestatic(CD_IntLiteral, "$new",
                            MethodTypeDesc.of(CD_IntLiteral, CD_Ctx, CD_String))
                    .aload(ctxSlot)
                    .invokevirtual(CD_IntLiteral, "to" + thisName,
                            MethodTypeDesc.of(thisCD, CD_Ctx))
                    .areturn();
            });
        } else if (isBitArray || isByteArray) {
            classBuilder.withMethodBody(jitName, jmd.standardMD, flags, code -> {
                MethodTypeDesc mdToBigInt = MethodTypeDesc.of(bigIntCD, CD_Ctx, CD_boolean);
                MethodTypeDesc mdBox      = MethodTypeDesc.of(thisCD, bigIntCD);
                ClassDesc      arrayCD    = jmd.standardParams[0].cd;
                int            ctxSlot    = code.parameterSlot(jmd.standardCtx());
                int            arraySlot  = code.parameterSlot(jmd.getImplicitParamCount());
                boolean        unsigned   = thisName.equals("UIntN");

                code.aload(arraySlot)
                    .aload(ctxSlot)
                    .loadConstant(unsigned ? 0 : 1)
                    .invokevirtual(arrayCD, "$toBigInteger", mdToBigInt)
                    .invokestatic(thisCD, "$box", mdBox)
                    .areturn();
            });
        }
    }
}
