package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import org.xvm.asm.constants.PropertyConstant;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;

/**
 * Common base for property related ops.
 */
public abstract class OpProperty extends Op
    {
    /**
     * Construct an op.
     *
     * @param constProperty  the property constant
     */
    protected OpProperty(PropertyConstant constProperty)
        {
        m_constProperty = constProperty;
        }

    /**
     * Deserialization constructor.
     *
     * @param in      the DataInput to read from
     * @param aconst  an array of constants used within the method
     */
    protected OpProperty(DataInput in, Constant[] aconst)
            throws IOException
        {
        m_nPropId = readPackedInt(in);
        }

    @Override
    public void write(DataOutput out, ConstantRegistry registry)
            throws IOException
        {
        out.writeByte(getOpCode());

        if (m_constProperty != null)
            {
            m_nPropId = encodeArgument(m_constProperty, registry);
            }

        writePackedLong(out, m_nPropId);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        registerArgument(m_constProperty, registry);
        }

    protected int m_nPropId;

    private PropertyConstant m_constProperty;
    }
