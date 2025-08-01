package org.xvm.asm.op;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import java.lang.constant.MethodTypeDesc;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpInvocable;

import org.xvm.asm.constants.MethodBody;
import org.xvm.asm.constants.MethodConstant;
import org.xvm.asm.constants.MethodInfo;

import org.xvm.javajit.BuildContext;
import org.xvm.javajit.BuildContext.Slot;
import org.xvm.javajit.Builder;
import org.xvm.javajit.JitMethodDesc;
import org.xvm.javajit.JitParamDesc;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

/**
 * NVOK_N0 rvalue-target, CONST-METHOD, #params:(rvalue)
 */
public class Invoke_N0
        extends OpInvocable {
    /**
     * Construct an NVOK_N0 op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     * @param aArgValue    the array of Argument values
     */
    public Invoke_N0(Argument argTarget, MethodConstant constMethod, Argument[] aArgValue) {
        super(argTarget, constMethod);

        m_aArgValue = aArgValue;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Invoke_N0(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);

        m_anArgValue = readIntArray(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_aArgValue != null) {
            m_anArgValue = encodeArguments(m_aArgValue, registry);
        }

        writeIntArray(out, m_anArgValue);
    }

    @Override
    public int getOpCode() {
        return OP_NVOK_N0;
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);

            return isDeferred(hTarget)
                    ? hTarget.proceed(frame, frameCaller ->
                         resolveArgs(frameCaller, frameCaller.popStack()))
                    : resolveArgs(frame, hTarget);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected int resolveArgs(Frame frame, ObjectHandle hTarget) {
        CallChain chain = getCallChain(frame, hTarget);

        try {
            ObjectHandle[] ahArg = frame.getArguments(m_anArgValue, chain.getMaxVars());

            if (anyDeferred(ahArg)) {
                Frame.Continuation stepNext = frameCaller ->
                    chain.invoke(frameCaller, hTarget, ahArg, A_IGNORE);
                return new Utils.GetArguments(ahArg, stepNext).doNext(frame);
            }
            return chain.invoke(frame, hTarget, ahArg, A_IGNORE);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        super.registerConstants(registry);

        registerArguments(m_aArgValue, registry);
    }

    @Override
    protected String getParamsString() {
        return getParamsString(m_anArgValue, m_aArgValue);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        Slot targetSlot = bctx.loadArgument(code, m_nTarget);

        MethodConstant idMethod   = (MethodConstant) bctx.getConstant(m_nMethodId);
        MethodInfo     infoMethod = targetSlot.type().ensureTypeInfo().getMethodById(idMethod);
        JitMethodDesc  jmd        = infoMethod.getJitDesc(bctx.typeSystem);
        MethodTypeDesc md         = jmd.optimizedMD;
        String         methodName = idMethod.getName();

        if (md == null) {
            md = jmd.standardMD;
        } else {
            methodName += Builder.OPT;
        }

        bctx.loadCtx(code);
        for (int i = 0, c = m_anArgValue.length; i < c; i++ ) {
            int iArg = m_anArgValue[i];
            if (iArg == A_DEFAULT) {
                JitParamDesc pd = jmd.getOptimizedParam(i);
                switch (pd.flavor) {
                    case SpecificWithDefault:
                        code.aconst_null();
                        break;

                    case PrimitiveWithDefault:
                        // default primitive with an additional `true`
                        Builder.defaultLoad(code, pd.cd);
                        code.iconst_1();
                        break;

                    default:
                        throw new IllegalStateException();
                }
            } else {
                bctx.loadArgument(code, iArg);
            }
        }
        if (infoMethod.getHead().getImplementation().getExistence() == MethodBody.Existence.Interface) {
            code.invokeinterface(targetSlot.cd(), methodName, md);
        } else {
            code.invokevirtual(targetSlot.cd(), methodName, md);
        }
    }

    // ----- fields --------------------------------------------------------------------------------

    private int[] m_anArgValue;

    private Argument[] m_aArgValue;
}
