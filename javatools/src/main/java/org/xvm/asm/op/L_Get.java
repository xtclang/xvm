package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Argument;
import org.xvm.asm.Constant;
import org.xvm.asm.OpProperty;
import org.xvm.asm.Scope;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * L_GET PROPERTY, lvalue ; get local property
 */
public class L_Get
        extends OpProperty
    {
    /**
     * Construct an L_GET op based on the specified arguments.
     *
     * @param idProp     the property id
     * @param argReturn  the return Argument
     */
    public L_Get(PropertyConstant idProp, Argument argReturn)
        {
        super(idProp);

        m_argReturn = argReturn;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public L_Get(DataInput in, Constant[] aconst)
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
        return OP_L_GET;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        ObjectHandle hTarget = frame.getThis();

        PropertyConstant constProperty = (PropertyConstant) frame.getConstant(m_nPropId);

        if (frame.isNextRegister(m_nRetValue))
            {
            frame.introducePropertyVar(m_nRetValue, A_THIS, m_nPropId);
            }

        return hTarget.getTemplate().getPropertyValue(frame, hTarget, constProperty, m_nRetValue);
        }

    @Override
    public void resetSimulation()
        {
        resetRegister(m_argReturn);
        }

    @Override
    public void simulate(Scope scope)
        {
        checkNextRegister(scope, m_argReturn, m_nRetValue);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        m_argReturn = registerArgument(m_argReturn, registry);
        }

    @Override
    public String toString()
        {
        return super.toString() + ", " + Argument.toIdString(m_argReturn, m_nRetValue);
        }

    private int m_nRetValue;

    private Argument m_argReturn;
    }
