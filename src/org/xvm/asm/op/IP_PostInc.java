package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpInPlace;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xRef.RefHandle;


/**
 * IP_INCA lvalue-target, lvalue ; T++ -> T
 */
public class IP_PostInc
        extends OpInPlace
    {
    /**
     * Construct an IP_INCA op.
     *
     * @param nTarget  the location to increment
     * @param nRet     the location to store the post-incremented value
     */
    public IP_PostInc(int nTarget, int nRet)
        {
        super((Argument) null, null);

        m_nTarget = nTarget;
        m_nRetValue = nRet;
        }

    /**
     * Construct an IP_INCA op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argReturn  the Argument to store the result into
     */
    public IP_PostInc(Argument argTarget, Argument argReturn)
        {
        super(argTarget, argReturn);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IP_PostInc(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_IP_INCA;
        }

    @Override
    protected int completeWithRegister(Frame frame, ObjectHandle hTarget)
        {
        switch (hTarget.getOpSupport().invokeNext(frame, hTarget, m_nTarget))
            {
            case R_NEXT:
                return frame.assignValue(m_nRetValue, hTarget);

            case R_CALL:
                frame.m_frameNext.setContinuation(frameCaller ->
                    frameCaller.assignValue(m_nRetValue, hTarget));
                return R_CALL;

            case R_EXCEPTION:
                return R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    @Override
    protected int completeWithVar(Frame frame, RefHandle hTarget)
        {
        return hTarget.getVarSupport().invokeVarPostInc(frame, hTarget, m_nRetValue);
        }

    @Override
    protected int completeWithProperty(Frame frame, String sProperty)
        {
        ObjectHandle hTarget = frame.getThis();

        return hTarget.getTemplate().invokePostInc(frame, hTarget, sProperty, m_nRetValue);
        }
    }