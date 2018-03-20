package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpInPlace;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xRefImpl.RefHandle;


/**
 * IP_DECA lvalue-target, lvalue ; T-- -> T
 */
public class IP_PostDec
        extends OpInPlace
    {
    /**
     * Construct an IP_DECA op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argReturn  the Argument to store the result into
     */
    public IP_PostDec(Argument argTarget, Argument argReturn)
        {
        super(argTarget, argReturn);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IP_PostDec(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_IP_DECA;
        }

    @Override
    protected int completeWithRegister(Frame frame, ObjectHandle hTarget)
        {
        switch (hTarget.getOpSupport().invokePrev(frame, hTarget, m_nTarget))
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
        return hTarget.getVarSupport().invokeVarPostDec(frame, hTarget, m_nRetValue);
        }

    @Override
    protected int completeWithProperty(Frame frame, String sProperty)
        {
        ObjectHandle hTarget = frame.getThis();

        return hTarget.getTemplate().invokePostDec(frame, hTarget, sProperty, m_nRetValue);
        }
    }