package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpInPlace;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;


/**
 * IP_INCB lvalue-target, lvalue  ; ++T -> T
 */
public class IP_PreInc
        extends OpInPlace
    {
    /**
     * Construct an IP_INCB op.
     *
     * @param nTarget  the location to increment
     * @param nRet     the location to store the post-incremented value
     */
    public IP_PreInc(int nTarget, int nRet)
        {
        super((Argument) null, null);

        m_nTarget = nTarget;
        m_nRetValue = nRet;
        }

    /**
     * Construct an IP_INCB op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argReturn  the Argument to store the result into
     */
    public IP_PreInc(Argument argTarget, Argument argReturn)
        {
        super(argTarget, argReturn);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IP_PreInc(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_IP_INCB;
        }

    @Override
    protected int completeWithRegister(Frame frame, ObjectHandle hTarget)
        {
        switch (hTarget.getOpSupport().invokeNext(frame, hTarget, Frame.RET_LOCAL))
            {
            case R_NEXT:
                {
                ObjectHandle hValueNew = frame.getFrameLocal();
                return frame.assignValues(new int[]{m_nRetValue, m_nTarget},
                    hValueNew, hValueNew);
                }

            case R_CALL:
                frame.m_frameNext.setContinuation(frameCaller ->
                    {
                    ObjectHandle hValueNew = frameCaller.getFrameLocal();
                    return frameCaller.assignValues(new int[]{m_nRetValue, m_nTarget},
                        hValueNew, hValueNew);
                    });
                return R_CALL;

            case R_EXCEPTION:
                return R_EXCEPTION;

            default:
                throw new IllegalStateException();
            }
        }

    @Override
    protected int completeWithProperty(Frame frame, String sProperty)
        {
        ObjectHandle hTarget = frame.getThis();

        return hTarget.getTemplate().invokePreInc(frame, hTarget, sProperty, m_nRetValue);
        }
    }