package org.xvm.asm.op;


import java.io.DataInput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpPropInPlace;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;


/**
 * PIP_INCB PROPERTY, rvalue-target, lvalue ; same as IP_INCB for a register
 */
public class PIP_PreInc
        extends OpPropInPlace
    {
    /**
     * Construct a PIP_INCB op.
     *
     * @param nPropId  the property to increment
     * @param nTarget  the object on which the property exists
     * @param nRet     the location to store the pre-incremented value
     */
    public PIP_PreInc(int nPropId, int nTarget, int nRet)
        {
        super(null, null, null);

        m_nPropId = nPropId;
        m_nTarget = nTarget;
        m_nRetValue = nRet;
        }

    /**
     * Construct a PIP_INCB op based on the passed arguments.
     *
     * @param constProperty  the property constant
     * @param argTarget    the target Argument
     * @param argReturn    the Argument to move the result into (Register or local property)
     */
    public PIP_PreInc(PropertyConstant constProperty, Argument argTarget, Argument argReturn)
        {
        super(constProperty, argTarget, argReturn);
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public PIP_PreInc(DataInput in, Constant[] aconst)
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
    protected int complete(Frame frame, ObjectHandle hTarget)
        {
        PropertyConstant constProperty = (PropertyConstant) frame.getConstant(m_nPropId);

        return hTarget.getTemplate().invokePreInc(
                frame, hTarget, constProperty.getName(), m_nRetValue);
        }
    }