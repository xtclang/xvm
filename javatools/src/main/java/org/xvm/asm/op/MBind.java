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
}
