package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpInvocable;

import org.xvm.asm.constants.MethodConstant;

import org.xvm.runtime.CallChain;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import org.xvm.runtime.template.xFunction;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * MBIND rvalue-target, CONST-METHOD, lvalue-fn-result
 */
public class MBind
        extends OpInvocable
    {
    /**
     * Construct an MBIND op based on the passed arguments.
     *
     * @param argTarget    the target argument
     * @param constMethod  the method constant
     * @param argReturn    the return value register
     */
    public MBind(Argument argTarget, MethodConstant constMethod, Argument argReturn)
        {
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
            throws IOException
        {
        super(in, aconst);

        m_nRetValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argReturn != null)
            {
            m_nRetValue = encodeArgument(m_argReturn, registry);
            }

        writePackedLong(out, m_nRetValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_MBIND;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hTarget = frame.getArgument(m_nTarget);
            if (hTarget == null)
                {
                return R_REPEAT;
                }

            if (isDeferred(hTarget))
                {
                ObjectHandle[] ahTarget = new ObjectHandle[] {hTarget};
                Frame.Continuation stepNext = frameCaller -> proceed(frameCaller, ahTarget[0]);

                return new Utils.GetArguments(ahTarget, stepNext).doNext(frame);
                }

            return proceed(frame, hTarget);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    protected int proceed(Frame frame, ObjectHandle hTarget)
        {
        CallChain chain = getCallChain(frame, hTarget);

        if (frame.isNextRegister(m_nRetValue))
            {
            // do we need a precise type?
            frame.introduceResolvedVar(m_nRetValue, frame.poolContext().typeFunction());
            }

        return frame.assignValue(m_nRetValue, hTarget.getTemplate().isService() ?
                xFunction.makeAsyncHandle(chain).bindTarget(hTarget) :
                xFunction.makeHandle(chain, 0).bindTarget(hTarget));
        }
    }
