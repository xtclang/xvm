package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpInPlace;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xRef.RefHandle;


/**
 * IP_DECB lvalue-target, lvalue  ; --T -> T
 */
public class IP_PreDec
        extends OpInPlace
    {
    /**
     * Construct an IP_DECB op for the passed arguments.
     *
     * @param argTarget  the target Argument
     * @param argReturn  the Argument to store the result into
     */
    public IP_PreDec(Argument argTarget, Argument argReturn)
        {
        super(argTarget, argReturn);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IP_PreDec(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_IP_DECB;
        }

    @Override
    protected int completeWithRegister(Frame frame, ObjectHandle hTarget)
        {
        switch (hTarget.getOpSupport().invokePrev(frame, hTarget, A_LOCAL))
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
    protected int completeWithVar(Frame frame, RefHandle hTarget)
        {
        return hTarget.getVarSupport().invokeVarPreDec(frame, hTarget, m_nRetValue);
        }

    @Override
    protected int completeWithProperty(Frame frame, String sProperty)
        {
        ObjectHandle hTarget = frame.getThis();

        return hTarget.getTemplate().invokePreDec(frame, hTarget, sProperty, m_nRetValue);
        }
    }