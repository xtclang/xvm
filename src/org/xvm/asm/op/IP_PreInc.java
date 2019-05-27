package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpInPlace;

import org.xvm.asm.constants.PropertyConstant;
import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xRef.RefHandle;


/**
 * IP_INCB lvalue-target, lvalue  ; ++T -> T
 */
public class IP_PreInc
        extends OpInPlace
    {
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
        switch (hTarget.getOpSupport().invokeNext(frame, hTarget, A_STACK))
            {
            case R_NEXT:
                {
                ObjectHandle hValueNew = frame.popStack();
                return frame.assignValues(new int[]{m_nRetValue, m_nTarget},
                    hValueNew, hValueNew);
                }

            case R_CALL:
                frame.m_frameNext.addContinuation(frameCaller ->
                    {
                    ObjectHandle hValueNew = frameCaller.popStack();
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
        return hTarget.getVarSupport().invokeVarPreInc(frame, hTarget, m_nRetValue);
        }

    @Override
    protected int completeWithProperty(Frame frame, PropertyConstant idProp)
        {
        ObjectHandle hTarget = frame.getThis();

        return hTarget.getTemplate().invokePreInc(frame, hTarget, idProp, m_nRetValue);
        }
    }