package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.ClassDesc;
import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpInvocable;

import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.TypeConstant;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.RegisterInfo;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static java.lang.constant.ConstantDescs.CD_MethodHandle;
import static java.lang.constant.ConstantDescs.CD_boolean;
import static java.lang.constant.ConstantDescs.CD_void;
import static java.lang.constant.ConstantDescs.INIT_NAME;

import static org.xvm.javajit.Builder.CD_Ctx;
import static org.xvm.javajit.Builder.CD_JavaObject;
import static org.xvm.javajit.Builder.CD_TypeConstant;
import static org.xvm.javajit.Builder.CD_nMethod;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * MBIND rvalue-target, CONST-METHOD, lvalue-fn-result
 */
public class MBind
        extends OpInvocable {
    /**
     * Construct an MBIND op based on the passed arguments.
     *
     * @param argTarget    the target argument
     * @param constMethod  the method constant
     * @param argReturn    the return value register
     */
    public MBind(Argument argTarget, MethodConstant constMethod, Argument argReturn) {
        super(argTarget, constMethod);

        m_argReturn = argReturn;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public MBind(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);

        m_nRetValue = readPackedInt(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_argReturn != null) {
            m_nRetValue = encodeArgument(m_argReturn, registry);
        }

        writePackedLong(out, m_nRetValue);
    }

    @Override
    public int getOpCode() {
        return OP_MBIND;
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);

            return isDeferred(hTarget)
                    ? hTarget.proceed(frame, frameCaller ->
                        proceed(frameCaller, frameCaller.popStack()))
                    : proceed(frame, hTarget);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected int proceed(Frame frame, ObjectHandle hTarget) {
        if (frame.isNextRegister(m_nRetValue)) {
            // do we need a precise type?
            frame.introduceResolvedVar(m_nRetValue, frame.poolContext().typeFunction());
        }

        return getCallChain(frame, hTarget).bindTarget(frame, hTarget, m_nRetValue);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void computeTypes(BuildContext bctx) {
        TypeConstant typeMethod = bctx.getArgumentType(m_nMethodId);

        bctx.typeMatrix.assign(getAddress(), m_nRetValue, bctx.pool().bindMethodTarget(typeMethod));
    }

    @Override
    public int build(BuildContext bctx, CodeBuilder code) {
        RegisterInfo regMethod = bctx.ensureRegister(code, m_nMethodId);
        RegisterInfo regTarget = bctx.ensureRegister(code, m_nTarget);

        assert regMethod.type().isMethod() && regMethod.cd() == CD_nMethod;

        /* The code we need to generate looks like the following:

              MethodHandle std = method.stdMethod.bindTo(target);
              MethodHandle opt = method.optMethod == null ? null : method.optMethod.bindTo(target);
              boolean      imm = target.$isImmut();
              retValue = new FunctionN(ctx, std, opt, imm);

           Note that the resulting function is immutable if the target is immutable.
        */

        regMethod = regMethod.load(code);
        code.getfield(CD_nMethod, "stdMethod", CD_MethodHandle);
        regTarget = regTarget.load(code);
        code.invokevirtual(CD_MethodHandle, "bindTo", MethodTypeDesc.of(CD_MethodHandle, CD_JavaObject));
        int slotStd = bctx.storeTempValue(code, CD_MethodHandle);

        java.lang.classfile.Label ifNull = code.newLabel();
        regMethod = regMethod.load(code);
        code.getfield(CD_nMethod, "optMethod", CD_MethodHandle)
            .dup()
            .ifnull(ifNull);
        regTarget = regTarget.load(code);
        code.invokevirtual(CD_MethodHandle, "bindTo", MethodTypeDesc.of(CD_MethodHandle, CD_JavaObject))
            .labelBinding(ifNull);
        int slotOpt = bctx.storeTempValue(code, CD_MethodHandle);

        regTarget = regTarget.load(code);
        code.invokevirtual(regTarget.cd(), "$isImmut", MethodTypeDesc.of(CD_boolean));
        int slotImm = bctx.storeTempValue(code, CD_boolean);

        TypeConstant typeFn = bctx.pool().bindMethodTarget(regMethod.type());
        ClassDesc    cdFn   = bctx.builder.ensureClassDesc(typeFn);
        code.new_(cdFn)
            .dup()
            .aload(code.parameterSlot(0)); // ctx
        bctx.loadTypeConstant(code, typeFn);
        code.aload(slotStd)
            .aload(slotOpt)
            .iload(slotImm)
            .invokespecial(cdFn, INIT_NAME, MethodTypeDesc.of(CD_void, CD_Ctx, CD_TypeConstant,
                    CD_MethodHandle, CD_MethodHandle, CD_boolean));

        RegisterInfo regRet = bctx.ensureRegInfo(m_nRetValue, typeFn, cdFn, "");
        bctx.storeValue(code, regRet, typeFn);
        return -1;
    }
}
