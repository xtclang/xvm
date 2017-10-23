package org.xvm.asm;


import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;

import static org.xvm.util.Handy.readPackedInt;
import static org.xvm.util.Handy.writePackedLong;

/**
 * Common base for property related ops.
 *
 * @author gg 2017.02.21
 */
public abstract class OpProperty extends Op
    {
    /**
     * Construct an op.
     *
     * @param argProperty  the property Argument
     */
    protected OpProperty(Argument argProperty)
        {
        m_argProperty = argProperty;
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

        if (m_argProperty != null)
            {
            m_nPropId = encodeArgument(m_argProperty, registry);
            }

        writePackedLong(out, m_nPropId);
        }

    @Override
    public void registerConstants(ConstantRegistry registry)
        {
        registerArgument(m_argProperty, registry);
        }

    protected int m_nPropId;

    protected Argument m_argProperty;
    }
