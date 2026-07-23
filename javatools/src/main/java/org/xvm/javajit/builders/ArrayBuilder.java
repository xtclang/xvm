package org.xvm.javajit.builders;

import java.lang.classfile.ClassBuilder;
import java.lang.classfile.ClassModel;
import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.ConstantPool;

import org.xvm.asm.constants.MethodInfo;
import org.xvm.asm.constants.SignatureConstant;
import org.xvm.asm.constants.TypeConstant;
import org.xvm.asm.constants.TypeInfo;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitParamDesc;
import org.xvm.javajit.TypeSystem;

/**
 * The builder for Array types.
 */
public class ArrayBuilder extends AugmentingBuilder {

    public ArrayBuilder(TypeSystem typeSystem, TypeConstant type, ClassModel model) {
        super(typeSystem, type, model);

        DELEGATE_TYPE = pool().ensureEcstasyTypeConstant("collections.Array.ArrayDelegate");
        isObjectArray = type.getParamType(0).equals(typeSystem.pool().typeObject());
    }

    protected final TypeConstant DELEGATE_TYPE;

    protected final boolean isObjectArray;

    @Override
    public ClassDesc ensureClassDesc(TypeConstant type) {
        if (isBaseArrayType(type)) {
            return CD_Array;
        }
        return super.ensureClassDesc(type);
    }

    @Override
    public String ensureJitClassName(TypeConstant type) {
        if (isBaseArrayType(type)) {
            return N_Array;
        }
        return super.ensureJitClassName(type);
    }

    protected boolean isBaseArrayType(TypeConstant type) {
        if (!type.isArray()) {
            // not an array
            return false;
        }
        if (!type.isParamsSpecified()) {
            // no parameter type specified, type is base array
            return true;
        }

        TypeConstant paramType = type.getParamType(0);
        if (paramType.isJitPrimitive()) {
            // parameter type is a JIT primitive, sp array is specialized
            return false;
        }
        if (isObjectArray && type.equals(this.thisType.removeAccess())) {
            // this builder is for Array<Object> and the type matches, so not base array
            return false;
        }
        // type is a base array
        return true;
    }

    @Override
    protected boolean isNativeField(String jitName, ClassDesc cd) {
        return jitName.equals("$type") || super.isNativeField(jitName, cd);
    }

    @Override
    protected void loadTypeConstant(CodeBuilder code, String className, TypeConstant type) {
        if (type.isArray()) {
            // call $xvmType() on the this Array instance
            ClassDesc CD_this = ClassDesc.of(className);
            code.aload(0) // load this
                .checkcast(CD_this)
                .aload(1) // load Ctx
                .invokevirtual(CD_this, "$xvmType", MethodTypeDesc.of(CD_TypeConstant, CD_Ctx));
        } else {
            super.loadTypeConstant(code, className, type);
        }
    }

    @Override
    protected boolean shouldAddInterface(TypeConstant type) {
        if (type.isA(DELEGATE_TYPE)) {
            // skip the ArrayDelegate
            return false;
        }
        return super.shouldAddInterface(type);
    }

    @Override
    protected void assembleImplProperties(String className, ClassBuilder classBuilder) {
        // don't create any properties for array specializations
        if (!isSpecialized && !isObjectArray) {
            super.assembleImplProperties(className, classBuilder);
        }
    }

    @Override
    protected void assembleMethod(String className, ClassBuilder classBuilder, MethodInfo method,
                                  String jitName, JitMethodDesc jmd) {
        if (method.isCtorOrValidator()) {
            // all constructors are native
            return;
        }
        super.assembleMethod(className, classBuilder, method, jitName, jmd);
    }

    @Override
    protected void doAssembleMethod(ClassBuilder classBuilder, BuildContext bctx, MethodInfo method,
                                    String jitName, MethodTypeDesc md, int flags) {
        if (isSpecialized || isObjectArray) {
            // generating code for a specialized array so generate a wrapper that calls the
            // method on the base array
            classBuilder.withMethod(jitName, md, flags, methodBuilder -> {
                if (!method.isAbstract()) {
                    methodBuilder.withCode(code -> generateSuperWrapper(md, bctx, code));
                }
            });
        } else {
            // generate the actual code for the base Array
            super.doAssembleMethod(classBuilder, bctx, method, jitName, md, flags);
        }
    }

    /**
     * Generate a wrapper method that calls the corresponding method on the base array.
     */
    protected void generateSuperWrapper(MethodTypeDesc md, BuildContext bctx, CodeBuilder code) {
        boolean           hasReturns    = bctx.methodStruct.getReturnCount() > 0;
        boolean           isStatic      = bctx.methodStruct.isFunction();
        ConstantPool      pool          = pool();
        TypeConstant      baseArrayType = pool.typeArray();
        TypeInfo          baseArrayInfo = typeSystem.ensureTypeInfo(baseArrayType);
        SignatureConstant sig           = bctx.methodStruct.resolveSignature(pool, baseArrayType);
        MethodInfo        baseMethod    = baseArrayInfo.getMethodBySignature(sig, true);

        if (baseMethod == null) {
            // TODO this is usually because the method is from a mixin
            // this obviously needs to be built somehow, but generation seems to fail if we just
            // call super.generateCode(md, bctx, code); we temporarily generate an
            // unsupported exception instead
            bctx.throwUnsupported(code);
//            super.generateCode(md, bctx, code);
            return;
        }

        JitMethodDesc  jmdBase = baseMethod.getJitDesc(this, baseArrayType);
        JitMethodDesc  jmdThis = bctx.methodDesc;
        String         jitName = bctx.methodJitName;
        MethodTypeDesc mdBase;

        if (jmdBase.isOptimized) {
            jitName += OPT;
            mdBase   = jmdBase.optimizedMD;
        } else {
            mdBase = jmdBase.standardMD;
        }

        code.aload(0); // load this
        bctx.loadCtx(code);

        // load the method call arguments
        int param = 1;
        for (int i = 0; i < jmdThis.standardParams.length; i++) {
            JitParamDesc stdPd = jmdThis.standardParams[i];
            if (jmdThis.isOptimized) {
                for (int index : jmdThis.getAllOptimizedParams(i)) {
                    JitParamDesc pd = jmdThis.optimizedParams[index];
                    load(code, pd.cd, code.parameterSlot(param++));
                }
            } else {
                load(code, stdPd.cd, code.parameterSlot(param++));
            }
            // we have to box if this method is optimized, the parameter is a JIT
            // primitive, but the base method parameter is not a JIT primitive
            if (jmdThis.isOptimized && stdPd.type.isJitPrimitive()
                && !jmdBase.standardParams[i].type.isJitPrimitive()) {
                box(code, stdPd.type);
            }
        }

        // invoke the super method
        if (isStatic) {
            code.invokestatic(CD_Array, jitName, mdBase);
        } else {
            code.invokevirtual(CD_Array, jitName, mdBase);
        }
        // handle any return value
        if (hasReturns) {
            ClassDesc cd = md.returnType();
            if (!cd.isPrimitive()) {
                code.checkcast(cd);
            }
            addReturn(code, cd);
        }
    }

    @Override
    protected void assembleNew(String className, ClassBuilder classBuilder,
                               MethodInfo constructor, String jitName, JitMethodDesc jmd) {
        // all constructors are native
    }

    @Override
    protected void assembleXvmType(String className, ClassBuilder classBuilder) {
        // Array.java implements "$xvmType(Ctx ctx)"
    }
}