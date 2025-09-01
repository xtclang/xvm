package org.xvm.asm.op;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import java.lang.classfile.CodeBuilder;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpInvocable;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.javajit.BuildContext;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;

/**
 * NVOK_11 rvalue-target, rvalue-method, rvalue-param, lvalue-return
 */
public class Invoke_11
        extends OpInvocable {
    /**
     * Construct an NVOK_11 op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     * @param argValue     the value Argument
     * @param argReturn    the Argument to move the result into
     */
    public Invoke_11(Argument argTarget, MethodConstant constMethod, Argument argValue, Argument argReturn) {
        super(argTarget, constMethod);

        m_argValue = argValue;
        m_argReturn = argReturn;
    }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public Invoke_11(DataInput in, Constant[] aconst)
            throws IOException {
        super(in, aconst);

        m_nArgValue = readPackedInt(in);
        m_nRetValue = readPackedInt(in);
    }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException {
        super.write(out, registry);

        if (m_argValue != null) {
            m_nArgValue = encodeArgument(m_argValue, registry);
            m_nRetValue  = encodeArgument(m_argReturn, registry);
        }

        writePackedLong(out, m_nArgValue);
        writePackedLong(out, m_nRetValue);
    }

    @Override
    public int getOpCode() {
        return OP_NVOK_11;
    }

    @Override
    public int process(Frame frame, int iPC) {
        try {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);
            ObjectHandle hArg    = frame.getArgument(m_nArgValue);

            return isDeferred(hTarget)
                    ? hTarget.proceed(frame, frameCaller ->
                        resolveArg(frameCaller, frameCaller.popStack(), hArg))
                    : resolveArg(frame, hTarget, hArg);
        } catch (ExceptionHandle.WrapperException e) {
            return frame.raiseException(e);
        }
    }

    protected int resolveArg(Frame frame, ObjectHandle hTarget, ObjectHandle hArg) {
        return isDeferred(hArg)
                ? hArg.proceed(frame, frameCaller ->
                    complete(frameCaller, hTarget, frameCaller.popStack()))
                : complete(frame, hTarget, hArg);
    }

    protected int complete(Frame frame, ObjectHandle hTarget, ObjectHandle hArg) {
        checkReturnRegister(frame, hTarget);

        return getCallChain(frame, hTarget).invoke(frame, hTarget, hArg, m_nRetValue);
    }

    @Override
    public void registerConstants(ConstantRegistry registry) {
        super.registerConstants(registry);

        m_argValue = registerArgument(m_argValue, registry);
    }

    @Override
    protected String getParamsString() {
        return Argument.toIdString(m_argValue, m_nArgValue);
    }

    // ----- JIT support ---------------------------------------------------------------------------

    @Override
    public void build(BuildContext bctx, CodeBuilder code) {
        buildInvoke(bctx, code, new int[] {m_nArgValue});
    }

    // ----- fields --------------------------------------------------------------------------------

    private int m_nArgValue;

    private Argument m_argValue;
}
