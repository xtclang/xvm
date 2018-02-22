package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpPropInPlace;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import org.xvm.runtime.template.xRef.RefHandle;


/**
 * PIP_INCA PROPERTY, rvalue-target, lvalue ; same as IP_INCA for a register
 */
public class PIP_PostInc
        extends OpPropInPlace
    {
    /**
     * Construct a PIP_INCA op.
     *
     * @param nPropId  the property to increment
     * @param nTarget  the object on which the property exists
     * @param nRet     the location to store the post-incremented value
     *
     * @deprecated
     */
    public PIP_PostInc(int nPropId, int nTarget, int nRet)
        {
        super(null, null, null);

        m_nPropId = nPropId;
        m_nTarget = nTarget;
        m_nRetValue = nRet;
        }

    /**
     * Construct a PIP_INCA op based on the passed arguments.
     *
     * @param constProperty  the property constant
     * @param argTarget      the target Argument
     * @param argReturn      the Argument to move the result into (Register or local property)
     */
    public PIP_PostInc(PropertyConstant constProperty, Argument argTarget, Argument argReturn)
        {
        super(constProperty, argTarget, argReturn);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public PIP_PostInc(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);
        }

    @Override
    public int getOpCode()
        {
        return OP_PIP_INCA;
        }

    @Override
    protected int completeRegular(Frame frame, ObjectHandle hTarget, String sPropName)
        {
        return hTarget.getTemplate().invokePostInc(frame, hTarget, sPropName, m_nRetValue);
        }

    @Override
    protected int completeRef(Frame frame, RefHandle hTarget)
        {
        return hTarget.getOpSupport().invokeNext(frame, hTarget, true, m_nRetValue);
        }
    }