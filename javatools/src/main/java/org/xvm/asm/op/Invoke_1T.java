package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpInvocable;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * NVOK_1T rvalue-target, CONST-METHOD, rvalue-param, lvalue-return-tuple
 */
public class Invoke_1T
        extends OpInvocable {
    /**
     * Construct an NVOK_1T op.
     *
     * @param nTarget    r-value that specifies the object on which the method being invoked
     * @param nMethodId  r-value that specifies the method being invoked
     * @param nArg       the r-value location of the method argument
     * @param nRet       the l-value location for the tuple result
     */
    public Invoke_1T(int nTarget, int nMethodId, int nArg, int nRet) {
        super((Argument) null, null);

        m_nTarget = nTarget;
        m_nMethodId = nMethodId;
        m_nArgValue = nArg;
        m_nRetValue = nRet;
    }

    /**
     * Construct an NVOK_1T op based on the passed arguments.
     *
     * @param argTarget    the target Argument
     * @param constMethod  the method constant
     * @param argValue     the value Argument
     * @param argReturn    the Argument to move the result into
     */
    public Invoke_1T(Argument argTarget, MethodConstant constMethod, Argument argValue, Argument argReturn) {
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
    public Invoke_1T(DataInput in, Constant[] aconst)
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
            m_nRetValue = encodeArgument(m_argReturn, registry);
        }

        writePackedLong(out, m_nArgValue);
        writePackedLong(out, m_nRetValue);
    }

    @Override
    public int getOpCode() {
        return OP_NVOK_1T;
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
        checkReturnTupleRegister(frame, hTarget);

        return getCallChain(frame, hTarget).invokeT(frame, hTarget, hArg, m_nRetValue);
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

    private int m_nArgValue;

    private Argument m_argValue;
}
