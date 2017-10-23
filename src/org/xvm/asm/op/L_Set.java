package org.xvm.asm.op;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.Constant;
import org.xvm.asm.OpProperty;

import org.xvm.asm.constants.PropertyConstant;

import org.xvm.runtime.Frame;
import org.xvm.runtime.ObjectHandle;
import org.xvm.runtime.ObjectHandle.ExceptionHandle;
import org.xvm.runtime.Utils;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;


/**
 * L_SET PROPERTY, rvalue ; set local property
 */
public class L_Set
        extends OpProperty
    {
    /**
     * Construct an L_SET op.
     *
     * @param nPropId  the property id
     * @param nValue   the value to set
     *
     * @deprecated
     */
    public L_Set(int nPropId, int nValue)
        {
        super(null);

        m_nPropId = nPropId;
        m_nValue = nValue;
        }

    /**
     * Construct an L_SET op based on the specified arguments.
     *
     * @param argProperty  the property Argument
     * @param argValue     the value Argument
     */
    public L_Set(Argument argProperty, Argument argValue)
        {
        super(argProperty);

        m_argValue = argValue;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    public L_Set(DataInput in, Constant[] aconst)
            throws IOException
        {
        super(in, aconst);

        m_nValue = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        super.write(out, registry);

        if (m_argValue != null)
            {
            m_nValue = encodeArgument(m_argValue, registry);
            }

        writePackedLong(out, m_nValue);
        }

    @Override
    public int getOpCode()
        {
        return OP_L_SET;
        }

    @Override
    public int process(Frame frame, int iPC)
        {
        try
            {
            ObjectHandle hValue = frame.getArgument(m_nValue);
            if (hValue == null)
                {
                return R_REPEAT;
                }

            ObjectHandle hTarget = frame.getThis();
            PropertyConstant constProperty = (PropertyConstant)
                    frame.f_context.f_pool.getConstant(m_nPropId);
            String sProperty = constProperty.getName();

            if (isProperty(hValue))
                {
                ObjectHandle[] ahValue = new ObjectHandle[] {hValue};
                Frame.Continuation stepNext = frameCaller -> hTarget.f_clazz.f_template.
                    setPropertyValue(frameCaller, hTarget, sProperty, ahValue[0]);

                return new Utils.GetArgument(ahValue, stepNext).doNext(frame);
                }

            return hTarget.f_clazz.f_template.setPropertyValue(frame, hTarget, sProperty, hValue);
            }
        catch (ExceptionHandle.WrapperException e)
            {
            return frame.raiseException(e);
            }
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        super.registerConstants(registry);

        registerArgument(m_argValue, registry);
        }

    private int m_nValue;

    private Argument m_argValue;
    }
