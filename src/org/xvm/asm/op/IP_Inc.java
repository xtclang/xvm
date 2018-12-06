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
 * IP_INC lvalue-target ; in-place increment; no result
 */
public class IP_Inc
        extends OpInPlace
    {
    /**
     * Construct an IP_INC op for the passed arguments.
     *
     * @param argTarget  the target Argument
     */
    public IP_Inc(Argument argTarget)
        {
        super(argTarget);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public IP_Inc(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_IP_INC;
        }

    @Override
    protected boolean isAssignOp()
        {
        return false;
        }

    @Override
    protected int completeWithRegister(Frame frame, ObjectHandle hTarget)
        {
        return hTarget.getOpSupport().invokeNext(frame, hTarget, m_nTarget);
        }

    @Override
    protected int completeWithVar(Frame frame, RefHandle hTarget)
        {
        return hTarget.getVarSupport().invokeVarPreInc(frame, hTarget, A_IGNORE);
        }

    @Override
    protected int completeWithProperty(Frame frame, String sProperty)
        {
        ObjectHandle hTarget = frame.getThis();

        return hTarget.getTemplate().invokePostInc(frame, hTarget, sProperty, A_IGNORE);
        }
    }